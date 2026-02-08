package com.honeymysteryworld.smartafk;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AfkManager {

    private final SmartAFK plugin;
    private final Map<UUID, AfkPlayer> players;
    private BukkitTask checkerTask;

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

    public AfkManager(SmartAFK plugin) {
        this.plugin = plugin;
        this.players = new ConcurrentHashMap<>();
        reloadSettings();
        startAfkChecker();
    }

    /**
     * Перезагрузка настроек из конфига
     */
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
    }

    /**
     * Остановка менеджера при выключении плагина
     */
    public void shutdown() {
        // Останавливаем таймер
        if (checkerTask != null) {
            checkerTask.cancel();
            checkerTask = null;
        }

        // Возвращаем всех АФК игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                AfkPlayer afkPlayer = players.get(player.getUniqueId());
                if (afkPlayer != null && afkPlayer.isAfk()) {
                    returnFromAfk(player, afkPlayer);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка возврата игрока " + player.getName() + ": " + e.getMessage());
            }
        }

        players.clear();
    }

    public AfkPlayer getAfkPlayer(Player player) {
        if (player == null) return null;
        return players.computeIfAbsent(player.getUniqueId(), AfkPlayer::new);
    }

    public void removePlayer(UUID uuid) {
        if (uuid != null) {
            players.remove(uuid);
        }
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

        // Уже в этом состоянии
        if (afkPlayer.isAfk() == afk) return;

        afkPlayer.setAfk(afk);

        try {
            if (afk) {
                onAfkStart(player, afkPlayer);
            } else {
                onAfkEnd(player, afkPlayer);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка изменения АФК статуса для " + player.getName() + ": " + e.getMessage());
        }
    }

    private void onAfkStart(Player player, AfkPlayer afkPlayer) {
        // Сохраняем состояние ДО телепорта
        afkPlayer.setReturnLocation(player.getLocation());
        afkPlayer.setWasFlying(player.isFlying());
        afkPlayer.setWasAllowFlight(player.getAllowFlight());

        // Оповещаем всех
        broadcastMessage("messages.afk-on", player);

        // Префикс в табе
        if (plugin.getConfig().getBoolean("settings.tab-prefix", true)) {
            String format = plugin.getConfig().getString("settings.tab-prefix-format", "&7[AFK] ");
            player.setPlayerListName(colorize(format) + player.getName());
        }

        // Телепорт в АФК мир
        if (afkWorldEnabled) {
            teleportToAfkWorld(player);
        }

        // Замораживаем чанки (1.14+)
        if (freezeChunks) {
            setChunkForceLoaded(afkPlayer.getReturnLocation(), false);
        }
    }

    private void onAfkEnd(Player player, AfkPlayer afkPlayer) {
        // Оповещаем всех
        broadcastMessage("messages.afk-off", player);

        // Убираем префикс в табе
        if (plugin.getConfig().getBoolean("settings.tab-prefix", true)) {
            player.setPlayerListName(player.getName());
        }

        // Возвращаем игрока
        returnFromAfk(player, afkPlayer);

        // Размораживаем чанки
        if (freezeChunks && afkPlayer.getReturnLocation() != null) {
            setChunkForceLoaded(afkPlayer.getReturnLocation(), true);
        }
    }

    private void returnFromAfk(Player player, AfkPlayer afkPlayer) {
        Location returnLoc = afkPlayer.getReturnLocation();

        if (returnLoc != null && returnLoc.getWorld() != null) {
            // Проверяем что мир загружен
            if (Bukkit.getWorld(returnLoc.getWorld().getName()) != null) {
                player.teleport(returnLoc);
                player.setAllowFlight(afkPlayer.wasAllowFlight());
                player.setFlying(afkPlayer.wasFlying());
            }
            afkPlayer.setReturnLocation(null);
        }
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
                        plugin.getLogger().warning("Ошибка проверки игрока " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void checkPlayer(Player player, long now) {
        AfkPlayer afkPlayer = getAfkPlayer(player);
        if (afkPlayer == null) return;

        long inactive = now - afkPlayer.getLastActivity();

        // Авто-АФК
        if (!afkPlayer.isAfk() && inactive >= afkTimeout) {
            setAfk(player, true);
            return;
        }

        // Проверки для АФК игроков
        if (!afkPlayer.isAfk()) return;
        if (kickTimeout <= 0) return;
        if (player.hasPermission("smartafk.bypass")) return;

        long timeLeft = kickTimeout - inactive;

        // Кик за долгий АФК
        if (timeLeft <= 0) {
            player.kickPlayer(colorize(kickMessage));
            return;
        }

        // Предупреждение о кике
        // Отправляем на 120, 90, 60, 30, 10, 5 секундах
        long secondsLeft = timeLeft / 1000;
        if (secondsLeft == 120 || secondsLeft == 90 || secondsLeft == 60 ||
                secondsLeft == 30 || secondsLeft == 10 || secondsLeft == 5) {

            // Проверяем что не отправляли уже
            if (afkPlayer.getLastWarningTime() != secondsLeft) {
                afkPlayer.setLastWarningTime(secondsLeft);
                String warning = colorize(kickWarningMessage.replace("{time}", String.valueOf(secondsLeft)));
                player.sendMessage(warning);
            }
        }
    }

    private void teleportToAfkWorld(Player player) {
        World afkWorld = Bukkit.getWorld(afkWorldName);

        // Создаём мир если не существует
        if (afkWorld == null) {
            afkWorld = createAfkWorld();
        }

        if (afkWorld != null) {
            Location afkSpawn = new Location(afkWorld, afkSpawnX, afkSpawnY, afkSpawnZ);
            player.teleport(afkSpawn);
            player.setAllowFlight(true);
            player.setFlying(true);
        } else {
            plugin.getLogger().warning("Не удалось создать/загрузить АФК-мир!");
        }
    }

    private World createAfkWorld() {
        plugin.getLogger().info("Создаю АФК-мир: " + afkWorldName);

        try {
            WorldCreator creator = new WorldCreator(afkWorldName);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);

            World world = creator.createWorld();

            if (world != null) {
                setupAfkWorld(world);
                plugin.getLogger().info("АФК-мир успешно создан!");
            }

            return world;
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка создания АФК-мира: " + e.getMessage());
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
        } catch (Exception e) {
            // Fallback для старых версий
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

    private void setChunkForceLoaded(Location location, boolean loaded) {
        if (location == null || location.getWorld() == null) return;
        if (!VersionUtil.hasForceLoaded()) return;

        try {
            Chunk chunk = location.getChunk();
            chunk.setForceLoaded(loaded);
        } catch (Exception ignored) {}
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