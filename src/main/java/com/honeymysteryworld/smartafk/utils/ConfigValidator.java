package com.honeymysteryworld.smartafk.utils;

import com.honeymysteryworld.smartafk.SmartAFK;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigValidator {

    private final SmartAFK plugin;
    private final Logger logger;
    private boolean hasErrors;

    public ConfigValidator(SmartAFK plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        this.hasErrors = false;
    }

    public boolean validate() {
        hasErrors = false;
        FileConfiguration config = plugin.getConfig();

        // Проверяем таймауты
        validatePositiveInt("afk-timeout", config.getInt("afk-timeout", 300), 30, 86400);
        validatePositiveInt("kick-timeout", config.getInt("kick-timeout", 1800), 0, 86400);

        // Проверяем что kick-timeout > afk-timeout (если включен)
        int afkTimeout = config.getInt("afk-timeout", 300);
        int kickTimeout = config.getInt("kick-timeout", 1800);
        if (kickTimeout > 0 && kickTimeout <= afkTimeout) {
            logger.warning("kick-timeout должен быть больше afk-timeout! Исправляю...");
            plugin.getConfig().set("kick-timeout", afkTimeout * 2);
            hasErrors = true;
        }

        // Проверяем координаты спавна
        validateDouble("afk-world.spawn-location.y", config.getDouble("afk-world.spawn-location.y", 100), -64, 320);

        // Проверяем название мира
        String worldName = config.getString("afk-world.world-name", "world_afk");
        if (worldName == null || worldName.trim().isEmpty()) {
            logger.warning("afk-world.world-name пустой! Использую 'world_afk'");
            plugin.getConfig().set("afk-world.world-name", "world_afk");
            hasErrors = true;
        }

        // Сохраняем если были исправления
        if (hasErrors) {
            plugin.saveConfig();
            logger.info("Конфиг исправлен и сохранён");
        }

        return !hasErrors;
    }

    private void validatePositiveInt(String path, int value, int min, int max) {
        if (value < min) {
            logger.warning(path + " слишком маленький (" + value + "). Минимум: " + min);
            plugin.getConfig().set(path, min);
            hasErrors = true;
        } else if (value > max) {
            logger.warning(path + " слишком большой (" + value + "). Максимум: " + max);
            plugin.getConfig().set(path, max);
            hasErrors = true;
        }
    }

    private void validateDouble(String path, double value, double min, double max) {
        if (value < min || value > max) {
            logger.warning(path + " вне диапазона (" + value + "). Допустимо: " + min + "-" + max);
            plugin.getConfig().set(path, Math.max(min, Math.min(max, value)));
            hasErrors = true;
        }
    }

    public boolean hasErrors() {
        return hasErrors;
    }
}