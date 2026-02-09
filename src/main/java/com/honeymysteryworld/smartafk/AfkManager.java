package com.honeymysteryworld.smartafk;

import com.honeymysteryworld.smartafk.utils.BackupManager;
import com.honeymysteryworld.smartafk.utils.Logger;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkManager {

    private final SmartAFK plugin;
    private final Logger logger;
    private final BackupManager backupManager;
    private final Map<UUID, AfkPlayer> players;
    private BukkitTask checkerTask;
    private World afkWorld; // Кэшируем мир

    // Кэш настроек
    private int afkTimeout;
    private int kickTimeout;
    private boolean afkWorldEnabled;
    private boolean freezeChunks;
    private String afkWorldName;
    private double afkSpawnX;
    private double afkSpawnY;
    private double afkSpawnZ;
    private String kickMessage;
    private String kickWarningMessage;

    public AfkManager(SmartAFK plugin, Logger logger, BackupManager backupManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.backupManager = backupManager;
        this.players = new ConcurrentHashMap<>();
        reloadSettings();

        // === FIX #2: Создаём мир при старте, а не при первом АФК ===
        if (afkWorldEnabled) {
            initAfkWorld();
        }

        startAfkChecker();
    }

    public void reloadSettings() {
        afkTimeout = plugin.getConfig().getInt("afk-timeout", 300) * 1000;
        kickTimeout = plugin.getConfig().getInt("kick-timeout", 1800) * 1000;
        afkWorldEnabled = plugin.getConfig().getBoolean("afk-world.enabled", true);
        freezeChunks = plugin.getConfig().getBoolean("freeze-chunks", true);
        afkWorldName = plugin.getConfig().getString("afk-world.world-name", "world_afk");
        afkSpawnX = plugin.getConfig().getDouble("afk-world.spawn-location.x", 0.5);
        afkSpawnY = plugin.getConfig().getDouble("afk-world.spawn-location.y", 100);
        afkSpawnZ = plugin.getConfig().getDouble("afk-world.spawn-location.z", 0.5);
        kickMessage = plugin.getConfig().getString("kick-message", "&cВы были кикнуты за долгий АФК");
        kickWarningMessage = plugin.getConfig().getString("messages.afk-kick-warning", "&cКик через {time} секунд!");

        logger.debug("Настройки перезагружены");
    }

    /**
     * FIX #2: Инициализация АФК мира при старте плагина
     */
    private void initAfkWorld() {
        afkWorld = Bukkit.getWorld(afkWorldName);

        if (afkWorld == null) {
            logger.info("Создаю АФК-мир при старте: " + afkWorldName);
            afkWorld = createAfkWorld();
        } else {
            logger.info("АФК-мир загружен: " + afkWorldName);
        }
    }

    public void shutdown() {
        if (checkerTask != null) {
            checkerTask.cancel();
            checkerTask = null;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                AfkPlayer afkPlayer = players.get(player.getUniqueId());
                if (afkPlayer != null && afkPlayer.isAfk()) {
                    // === FIX #7: Сбрасываем имя в табе ===
                    player.setPlayerListName(player.getName());
                    returnFromAfk(player, afkPlayer);
                    logger.debug("Возвращён игрок: " + player.getName());
                }
            } catch (Exception e) {
                logger.error("Ошибка возврата игрока " + player.getName(), e);
            }
        }

        players.clear();
        logger.debug("AfkManager остановлен");
    }

    public AfkPlayer getAfkPlayer(Player player) {
        if (player == null) return null;
        return players.computeIfAbsent(player.getUniqueId(), AfkPlayer::new);
    }

    public void removePlayer(UUID uuid) {
        if (uuid != null) {
            players.remove(uuid);
            logger.debug("Удалён игрок: " + uuid);
        }
    }

    /**
     * FIX #7: Вызывается при выходе игрока — сбрасываем имя
     */
    public void handlePlayerQuit(Player player) {
        if (player == null) return;

        AfkPlayer afkPlayer = players.get(player.getUniqueId());
        if (afkPlayer != null && afkPlayer.isAfk()) {
            // Сбрасываем имя в табе
            player.setPlayerListName(player.getName());

            // Возвращаем на место перед выходом
            if (afkPlayer.hasReturnLocation()) {
                player.teleport(afkPlayer.getReturnLocation());
            }
        }

        removePlayer(player.getUniqueId());
    }

    public void updateActivity(Player player) {
        if (player == null || !player.isOnline()) return;

        AfkPlayer afkPlayer = getAfkPlayer(player);
        if (afkPlayer == null) return;

        afkPlayer.updateActivity();

        if (afkPlayer.isAfk()) {
            setAfk(player, false);
        }
    }

    public void setAfk(Player player, boolean afk) {
        if (player == null || !player.isOnline()) return;

        AfkPlayer afkPlayer = getAfkPlayer(player);
        if (afkPlayer == null) return;

        if (afkPlayer.isAfk() == afk) return;

        afkPlayer.setAfk(afk);

        try {
            if (afk) {
                onAfkStart(player, afkPlayer);
            } else {
                onAfkEnd(player, afkPlayer);
            }

            backupManager.saveBackup(players);

        } catch (Exception e) {
            logger.error("Ошибка изменения АФК статуса для " + player.getName(), e);
        }
    }

    private void onAfkStart(Player player, AfkPlayer afkPlayer) {
        // Сохраняем состояние
        afkPlayer.setReturnLocation(player.getLocation());
        afkPlayer.setWasFlying(player.isFlying());
        afkPlayer.setWasAllowFlight(player.getAllowFlight());
        afkPlayer.setWasHealth(player.getHealth());
        afkPlayer.setWasFood(player.getFoodLevel());
        afkPlayer.setWasSaturation(player.getSaturation());

        // Сбрасываем падение (урон получит при возврате если нужно)
        afkPlayer.setWasFallDistance(player.getFallDistance());
        player.setFallDistance(0);

        // Выходим из вагонетки/лодки
        if (player.isInsideVehicle()) {
            afkPlayer.setWasInVehicle(true);
            player.leaveVehicle();
        }

        broadcastMessage("messages.afk-on", player);

        if (plugin.getConfig().getBoolean("settings.tab-prefix", true)) {
            String format = plugin.getConfig().getString("settings.tab-prefix-format", "&7[AFK] ");
            player.setPlayerListName(colorize(format) + player.getName());
        }

        if (afkWorldEnabled && afkWorld != null) {
            teleportToAfkWorld(player);

            // === FIX #3: Делаем игрока бессмертным в АФК мире ===
            player.setInvulnerable(true);
        }

        // === FIX #1: Убираем setForceLoaded(false) — не нужно ===
        // Чанк и так выгрузится когда игрок уйдёт

        logger.debug("АФК старт: " + player.getName());
    }

    private void onAfkEnd(Player player, AfkPlayer afkPlayer) {
        broadcastMessage("messages.afk-off", player);

        if (plugin.getConfig().getBoolean("settings.tab-prefix", true)) {
            player.setPlayerListName(player.getName());
        }

        // === FIX #3: Убираем бессмертие ===
        player.setInvulnerable(false);

        returnFromAfk(player, afkPlayer);

        // === FIX #6: Сбрасываем урон от падения при возврате ===
        // Чтобы не было случайных смертей от лагов физики
        player.setFallDistance(0);

        // === FIX #1: Убираем setForceLoaded(true) — это утечка памяти! ===
        // Чанк и так загрузится от телепорта игрока

        logger.debug("АФК конец: " + player.getName());
    }

    private void returnFromAfk(Player player, AfkPlayer afkPlayer) {
        Location returnLoc = afkPlayer.getReturnLocation();

        // === FIX #4: Если локация null — телепортируем на спавн ===
        if (returnLoc == null || returnLoc.getWorld() == null) {
            logger.warning("Нет локации возврата для " + player.getName() + ", телепортирую на спавн");
            World mainWorld = Bukkit.getWorlds().get(0);
            if (mainWorld != null) {
                player.teleport(mainWorld.getSpawnLocation());
            }
            afkPlayer.setReturnLocation(null);
            return;
        }

        // Проверяем что мир загружен
        if (Bukkit.getWorld(returnLoc.getWorld().getName()) == null) {
            logger.warning("Мир " + returnLoc.getWorld().getName() + " не загружен, телепортирую на спавн");
            World mainWorld = Bukkit.getWorlds().get(0);
            if (mainWorld != null) {
                player.teleport(mainWorld.getSpawnLocation());
            }
            afkPlayer.setReturnLocation(null);
            return;
        }

        // === FIX #5: Проверяем безопасность локации ===
        Location safeLoc = findSafeLocation(returnLoc);

        player.teleport(safeLoc);
        player.setAllowFlight(afkPlayer.wasAllowFlight());
        player.setFlying(afkPlayer.wasFlying());

        // Восстанавливаем здоровье и голод
        player.setHealth(Math.min(afkPlayer.getWasHealth(), player.getMaxHealth()));
        player.setFoodLevel(afkPlayer.getWasFood());
        player.setSaturation(afkPlayer.getWasSaturation());

        afkPlayer.setReturnLocation(null);
    }

    /**
     * FIX #5: Поиск безопасной локации для телепорта
     */
    private Location findSafeLocation(Location loc) {
        if (isSafeLocation(loc)) {
            return loc;
        }

        // Ищем безопасное место выше
        Location checkLoc = loc.clone();
        for (int y = 0; y < 10; y++) {
            checkLoc.setY(loc.getY() + y);
            if (isSafeLocation(checkLoc)) {
                logger.debug("Найдена безопасная локация на Y+" + y);
                return checkLoc;
            }
        }

        // Ищем безопасное место ниже
        checkLoc = loc.clone();
        for (int y = 0; y < 10; y++) {
            checkLoc.setY(loc.getY() - y);
            if (isSafeLocation(checkLoc)) {
                logger.debug("Найдена безопасная локация на Y-" + y);
                return checkLoc;
            }
        }

        // Не нашли — возвращаем оригинал
        logger.warning("Не найдена безопасная локация, телепортирую на оригинальную");
        return loc;
    }

    /**
     * Проверка безопасности локации
     */
    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        // Проверяем что ноги и голова — воздух
        if (!feet.getType().isAir() || !head.getType().isAir()) {
            return false;
        }

        // Проверяем что под ногами не воздух и не лава
        Material groundType = ground.getType();
        if (groundType.isAir() || groundType == Material.LAVA || groundType == Material.FIRE) {
            return false;
        }

        return true;
    }

    public void toggleAfk(Player player) {
        if (player == null) return;

        AfkPlayer afkPlayer = getAfkPlayer(player);
        if (afkPlayer != null) {
            setAfk(player, !afkPlayer.isAfk());
        }
    }

    public Map<UUID, AfkPlayer> getPlayers() {
        return players;
    }

    public int getAfkCount() {
        int count = 0;
        for (AfkPlayer ap : players.values()) {
            if (ap.isAfk()) count++;
        }
        return count;
    }

    private void startAfkChecker() {
        checkerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        checkPlayer(player, now);
                    } catch (Exception e) {
                        logger.error("Ошибка проверки игрока " + player.getName(), e);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        logger.debug("АФК чекер запущен");
    }

    private void checkPlayer(Player player, long now) {
        AfkPlayer afkPlayer = getAfkPlayer(player);
        if (afkPlayer == null) return;

        long inactive = now - afkPlayer.getLastActivity();

        if (!afkPlayer.isAfk() && inactive >= afkTimeout) {
            setAfk(player, true);
            return;
        }

        if (!afkPlayer.isAfk()) return;
        if (kickTimeout <= 0) return;
        if (player.hasPermission("smartafk.bypass")) return;

        long timeLeft = kickTimeout - inactive;

        if (timeLeft <= 0) {
            // === FIX #7: Сбрасываем имя перед киком ===
            player.setPlayerListName(player.getName());
            player.kickPlayer(colorize(kickMessage));
            logger.info("Кикнут за АФК: " + player.getName());
            return;
        }

        long secondsLeft = timeLeft / 1000;
        if (secondsLeft == 120 || secondsLeft == 90 || secondsLeft == 60 ||
                secondsLeft == 30 || secondsLeft == 10 || secondsLeft == 5) {

            if (afkPlayer.getLastWarningTime() != secondsLeft) {
                afkPlayer.setLastWarningTime(secondsLeft);
                String warning = colorize(kickWarningMessage.replace("{time}", String.valueOf(secondsLeft)));
                player.sendMessage(warning);
            }
        }
    }

    private void teleportToAfkWorld(Player player) {
        // === FIX #2: Мир уже создан при старте ===
        if (afkWorld == null) {
            afkWorld = Bukkit.getWorld(afkWorldName);
        }

        if (afkWorld == null) {
            logger.warning("АФК-мир недоступен! Игрок остаётся на месте.");
            return;
        }

        Location afkSpawn = new Location(afkWorld, afkSpawnX, afkSpawnY, afkSpawnZ);
        player.teleport(afkSpawn);
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private World createAfkWorld() {
        logger.info("Создаю АФК-мир: " + afkWorldName);

        try {
            WorldCreator creator = new WorldCreator(afkWorldName);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            creator.generator(new EmptyWorldGenerator());

            World world = creator.createWorld();

            if (world != null) {
                setupAfkWorld(world);
                logger.info("АФК-мир создан!");
            }

            return world;
        } catch (Exception e) {
            logger.error("Ошибка создания АФК-мира", e);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private void setupAfkWorld(World world) {
        try {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false);
            world.setGameRule(GameRule.FALL_DAMAGE, false);
        } catch (Exception e) {
            try {
                world.setGameRuleValue("doMobSpawning", "false");
                world.setGameRuleValue("doDaylightCycle", "false");
                world.setGameRuleValue("doWeatherCycle", "false");
                world.setGameRuleValue("randomTickSpeed", "0");
                world.setGameRuleValue("doFireTick", "false");
            } catch (Exception ignored) {}
        }

        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(Difficulty.PEACEFUL);
    }

    private void broadcastMessage(String path, Player player) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString(path, "");

        if (msg.isEmpty()) return;

        msg = colorize(prefix + msg.replace("{player}", player.getName()));
        Bukkit.broadcastMessage(msg);
    }

    private String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}