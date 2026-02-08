package com.honeymysteryworld.smartafk;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AfkManager {

    private final SmartAFK plugin;
    private final Map<UUID, AfkPlayer> players;

    public AfkManager(SmartAFK plugin) {
        this.plugin = plugin;
        this.players = new HashMap<>();
        startAfkChecker();
    }

    public AfkPlayer getAfkPlayer(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), AfkPlayer::new);
    }

    public void removePlayer(UUID uuid) {
        players.remove(uuid);
    }

    public void updateActivity(Player player) {
        AfkPlayer afkPlayer = getAfkPlayer(player);
        afkPlayer.updateActivity();

        if (afkPlayer.isAfk()) {
            setAfk(player, false);
        }
    }

    public void setAfk(Player player, boolean afk) {
        AfkPlayer afkPlayer = getAfkPlayer(player);

        if (afkPlayer.isAfk() == afk) return;

        afkPlayer.setAfk(afk);

        if (afk) {
            onAfkStart(player, afkPlayer);
        } else {
            onAfkEnd(player, afkPlayer);
        }
    }

    private void onAfkStart(Player player, AfkPlayer afkPlayer) {
        // Оповещаем всех
        broadcastMessage("messages.afk-on", player);

        // Сохраняем состояние
        afkPlayer.setReturnLocation(player.getLocation());
        afkPlayer.setWasFlying(player.isFlying());
        afkPlayer.setWasAllowFlight(player.getAllowFlight());

        // Телепорт в АФК мир
        if (plugin.getConfig().getBoolean("afk-world.enabled", true)) {
            teleportToAfkWorld(player);
        }

        // Замораживаем чанки (1.14+)
        if (plugin.getConfig().getBoolean("freeze-chunks", true)) {
            setChunkForceLoaded(player, false);
        }
    }

    private void onAfkEnd(Player player, AfkPlayer afkPlayer) {
        // Оповещаем всех
        broadcastMessage("messages.afk-off", player);

        // Телепорт обратно
        Location returnLoc = afkPlayer.getReturnLocation();
        if (returnLoc != null && returnLoc.getWorld() != null) {
            player.teleport(returnLoc);
            player.setAllowFlight(afkPlayer.wasAllowFlight());
            player.setFlying(afkPlayer.wasFlying());
            afkPlayer.setReturnLocation(null);
        }

        // Размораживаем чанки
        if (plugin.getConfig().getBoolean("freeze-chunks", true)) {
            setChunkForceLoaded(player, true);
        }
    }

    public void toggleAfk(Player player) {
        AfkPlayer afkPlayer = getAfkPlayer(player);
        setAfk(player, !afkPlayer.isAfk());
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
        new BukkitRunnable() {
            @Override
            public void run() {
                int timeout = plugin.getConfig().getInt("afk-timeout", 300) * 1000;
                int kickTimeout = plugin.getConfig().getInt("kick-timeout", 1800) * 1000;

                long now = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    AfkPlayer afkPlayer = getAfkPlayer(player);
                    long inactive = now - afkPlayer.getLastActivity();

                    // Авто-АФК
                    if (!afkPlayer.isAfk() && inactive >= timeout) {
                        setAfk(player, true);
                    }

                    // Кик за долгий АФК
                    if (afkPlayer.isAfk() && kickTimeout > 0 && inactive >= kickTimeout) {
                        if (!player.hasPermission("smartafk.bypass")) {
                            String kickMsg = colorize(plugin.getConfig()
                                    .getString("kick-message", "AFK Kick"));
                            player.kickPlayer(kickMsg);
                            continue;
                        }
                    }

                    // Предупреждение о кике (каждые 30 сек в последние 2 минуты)
                    if (afkPlayer.isAfk() && kickTimeout > 0 && !player.hasPermission("smartafk.bypass")) {
                        long timeLeft = kickTimeout - inactive;
                        if (timeLeft > 0 && timeLeft <= 120000) {
                            if ((timeLeft / 1000) % 30 == 0) {
                                String warning = colorize(plugin.getConfig()
                                        .getString("messages.afk-kick-warning",
                                                "&cКик через {time} секунд!"))
                                        .replace("{time}", String.valueOf(timeLeft / 1000));
                                player.sendMessage(warning);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void teleportToAfkWorld(Player player) {
        String worldName = plugin.getConfig().getString("afk-world.world-name", "world_afk");
        World afkWorld = Bukkit.getWorld(worldName);

        // Создаём мир если не существует
        if (afkWorld == null) {
            plugin.getLogger().info("Создаю АФК-мир: " + worldName);

            WorldCreator creator = new WorldCreator(worldName);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            afkWorld = creator.createWorld();

            if (afkWorld != null) {
                setupAfkWorld(afkWorld);
            }
        }

        if (afkWorld != null) {
            double x = plugin.getConfig().getDouble("afk-world.spawn-location.x", 0.5);
            double y = plugin.getConfig().getDouble("afk-world.spawn-location.y", 100);
            double z = plugin.getConfig().getDouble("afk-world.spawn-location.z", 0.5);

            Location afkSpawn = new Location(afkWorld, x, y, z);
            player.teleport(afkSpawn);
            player.setAllowFlight(true);
            player.setFlying(true);
        }
    }

    @SuppressWarnings("deprecation")
    private void setupAfkWorld(World world) {
        // Работает на всех версиях 1.13+
        try {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            world.setGameRule(GameRule.DO_FIRE_TICK, false);
        } catch (Exception e) {
            // Для старых версий используем строковый метод
            world.setGameRuleValue("doMobSpawning", "false");
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setGameRuleValue("doWeatherCycle", "false");
            world.setGameRuleValue("randomTickSpeed", "0");
            world.setGameRuleValue("doFireTick", "false");
        }

        world.setTime(6000); // День
        world.setStorm(false);
        world.setThundering(false);
    }

    private void setChunkForceLoaded(Player player, boolean loaded) {
        // setForceLoaded появился в 1.14
        if (!VersionUtil.hasForceLoaded()) return;

        try {
            Chunk chunk = player.getLocation().getChunk();
            chunk.setForceLoaded(loaded);
        } catch (Exception ignored) {
            // Игнорируем если метод недоступен
        }
    }

    private void broadcastMessage(String path, Player player) {
        String msg = plugin.getConfig().getString(path, "");
        if (msg.isEmpty()) return;

        msg = colorize(msg.replace("{player}", player.getName()));
        Bukkit.broadcastMessage(msg);
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}