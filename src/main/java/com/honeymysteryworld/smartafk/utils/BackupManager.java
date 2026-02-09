package com.honeymysteryworld.smartafk.utils;

import com.honeymysteryworld.smartafk.AfkPlayer;
import com.honeymysteryworld.smartafk.SmartAFK;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class BackupManager {

    private final SmartAFK plugin;
    private final Logger logger;
    private final File backupFile;
    private boolean enabled;

    public BackupManager(SmartAFK plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.backupFile = new File(plugin.getDataFolder(), "backup.yml");
        this.enabled = plugin.getConfig().getBoolean("settings.backup-locations", true);
    }

    public void reload() {
        this.enabled = plugin.getConfig().getBoolean("settings.backup-locations", true);
    }

    public void saveBackup(Map<UUID, AfkPlayer> players) {
        if (!enabled) return;

        try {
            YamlConfiguration config = new YamlConfiguration();

            for (Map.Entry<UUID, AfkPlayer> entry : players.entrySet()) {
                AfkPlayer afkPlayer = entry.getValue();

                if (afkPlayer.isAfk() && afkPlayer.getReturnLocation() != null) {
                    String uuid = entry.getKey().toString();
                    Location loc = afkPlayer.getReturnLocation();

                    config.set(uuid + ".world", loc.getWorld().getName());
                    config.set(uuid + ".x", loc.getX());
                    config.set(uuid + ".y", loc.getY());
                    config.set(uuid + ".z", loc.getZ());
                    config.set(uuid + ".yaw", loc.getYaw());
                    config.set(uuid + ".pitch", loc.getPitch());
                    config.set(uuid + ".flying", afkPlayer.wasFlying());
                    config.set(uuid + ".allowFlight", afkPlayer.wasAllowFlight());
                }
            }

            config.save(backupFile);
            logger.debug("Бэкап сохранён: " + players.size() + " игроков");

        } catch (Exception e) {
            logger.error("Ошибка сохранения бэкапа", e);
        }
    }

    public void loadBackup(Map<UUID, AfkPlayer> players) {
        if (!enabled || !backupFile.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(backupFile);

            for (String uuidStr : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);

                    String worldName = config.getString(uuidStr + ".world");
                    World world = Bukkit.getWorld(worldName);

                    if (world == null) continue;

                    double x = config.getDouble(uuidStr + ".x");
                    double y = config.getDouble(uuidStr + ".y");
                    double z = config.getDouble(uuidStr + ".z");
                    float yaw = (float) config.getDouble(uuidStr + ".yaw");
                    float pitch = (float) config.getDouble(uuidStr + ".pitch");
                    boolean flying = config.getBoolean(uuidStr + ".flying");
                    boolean allowFlight = config.getBoolean(uuidStr + ".allowFlight");

                    Location loc = new Location(world, x, y, z, yaw, pitch);

                    AfkPlayer afkPlayer = players.computeIfAbsent(uuid, AfkPlayer::new);
                    afkPlayer.setReturnLocation(loc);
                    afkPlayer.setWasFlying(flying);
                    afkPlayer.setWasAllowFlight(allowFlight);

                    logger.debug("Загружен бэкап для: " + uuidStr);

                } catch (Exception e) {
                    logger.warning("Ошибка загрузки бэкапа для " + uuidStr);
                }
            }

            // Удаляем файл после загрузки
            backupFile.delete();
            logger.info("Бэкап восстановлен");

        } catch (Exception e) {
            logger.error("Ошибка загрузки бэкапа", e);
        }
    }

    public void clearBackup() {
        if (backupFile.exists()) {
            backupFile.delete();
        }
    }
}