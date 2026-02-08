package com.honeymysteryworld.smartafk;

import org.bukkit.Bukkit;

public class VersionUtil {

    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;

    static {
        String version = Bukkit.getBukkitVersion();
        // Пример: 1.20.4-R0.1-SNAPSHOT

        String[] parts = version.split("-")[0].split("\\.");

        MAJOR_VERSION = Integer.parseInt(parts[1]); // 20

        if (parts.length > 2) {
            MINOR_VERSION = Integer.parseInt(parts[2]); // 4
        } else {
            MINOR_VERSION = 0;
        }
    }

    /**
     * Получить мажорную версию (1.XX)
     * Например для 1.20.4 вернёт 20
     */
    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * Получить минорную версию (1.XX.X)
     * Например для 1.20.4 вернёт 4
     */
    public static int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * Проверить, версия >= указанной
     */
    public static boolean isAtLeast(int major) {
        return MAJOR_VERSION >= major;
    }

    /**
     * Проверить, версия >= указанной
     */
    public static boolean isAtLeast(int major, int minor) {
        if (MAJOR_VERSION > major) return true;
        if (MAJOR_VERSION == major) return MINOR_VERSION >= minor;
        return false;
    }

    /**
     * Есть ли поддержка GameRule enum (1.13+)
     */
    public static boolean hasGameRuleEnum() {
        return isAtLeast(13);
    }

    /**
     * Есть ли поддержка setForceLoaded (1.14+)
     */
    public static boolean hasForceLoaded() {
        return isAtLeast(14);
    }

    /**
     * Строковое представление версии
     */
    public static String getVersionString() {
        return "1." + MAJOR_VERSION + "." + MINOR_VERSION;
    }
}