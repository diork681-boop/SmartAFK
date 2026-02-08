package com.honeymysteryworld.smartafk;

import org.bukkit.Bukkit;

public final class VersionUtil {

    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;
    private static final int PATCH_VERSION;
    private static final String SERVER_TYPE;
    private static final String VERSION_STRING;

    // Кэш проверок
    private static final boolean HAS_FORCE_LOADED;
    private static final boolean HAS_GAME_RULE_ENUM;
    private static final boolean HAS_ADVENTURE_API;
    private static final boolean HAS_FOLIA;

    static {
        int major = 0;
        int minor = 0;
        int patch = 0;
        String serverType = "Unknown";

        try {
            // Парсим версию
            // Пример: 1.20.4-R0.1-SNAPSHOT или 1.20-R0.1-SNAPSHOT
            String bukkitVersion = Bukkit.getBukkitVersion();
            String[] parts = bukkitVersion.split("-")[0].split("\\.");

            if (parts.length >= 2) {
                major = Integer.parseInt(parts[1]);
            }
            if (parts.length >= 3) {
                minor = Integer.parseInt(parts[2]);
            }

            // Определяем тип сервера
            String serverName = Bukkit.getName().toLowerCase();
            if (serverName.contains("folia")) {
                serverType = "Folia";
            } else if (serverName.contains("paper")) {
                serverType = "Paper";
            } else if (serverName.contains("purpur")) {
                serverType = "Purpur";
            } else if (serverName.contains("pufferfish")) {
                serverType = "Pufferfish";
            } else if (serverName.contains("spigot")) {
                serverType = "Spigot";
            } else if (serverName.contains("bukkit") || serverName.contains("craftbukkit")) {
                serverType = "Bukkit";
            } else {
                serverType = Bukkit.getName();
            }

        } catch (Exception e) {
            Bukkit.getLogger().warning("[SmartAFK] Не удалось определить версию сервера: " + e.getMessage());
            major = 20; // Fallback
            minor = 0;
        }

        MAJOR_VERSION = major;
        MINOR_VERSION = minor;
        PATCH_VERSION = patch;
        SERVER_TYPE = serverType;
        VERSION_STRING = "1." + MAJOR_VERSION + (MINOR_VERSION > 0 ? "." + MINOR_VERSION : "");

        // Кэшируем проверки
        HAS_FORCE_LOADED = MAJOR_VERSION >= 14;
        HAS_GAME_RULE_ENUM = MAJOR_VERSION >= 13;
        HAS_ADVENTURE_API = checkAdventureApi();
        HAS_FOLIA = serverType.equals("Folia");
    }

    // Приватный конструктор — утилитный класс
    private VersionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Версии ====================

    /**
     * Мажорная версия (1.XX)
     * Для 1.20.4 вернёт 20
     */
    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    /**
     * Минорная версия (1.XX.X)
     * Для 1.20.4 вернёт 4
     */
    public static int getMinorVersion() {
        return MINOR_VERSION;
    }

    /**
     * Строковое представление: "1.20.4"
     */
    public static String getVersionString() {
        return VERSION_STRING;
    }

    /**
     * Тип сервера: Paper, Spigot, Purpur, и т.д.
     */
    public static String getServerType() {
        return SERVER_TYPE;
    }

    /**
     * Полная информация: "Paper 1.20.4"
     */
    public static String getFullVersion() {
        return SERVER_TYPE + " " + VERSION_STRING;
    }

    // ==================== Проверки версий ====================

    /**
     * Версия >= указанной мажорной
     */
    public static boolean isAtLeast(int major) {
        return MAJOR_VERSION >= major;
    }

    /**
     * Версия >= указанной мажорной.минорной
     */
    public static boolean isAtLeast(int major, int minor) {
        if (MAJOR_VERSION > major) return true;
        if (MAJOR_VERSION == major) return MINOR_VERSION >= minor;
        return false;
    }

    /**
     * Версия между min и max (включительно)
     */
    public static boolean isBetween(int minMajor, int maxMajor) {
        return MAJOR_VERSION >= minMajor && MAJOR_VERSION <= maxMajor;
    }

    // ==================== Проверки API ====================

    /**
     * Есть GameRule enum (1.13+)
     */
    public static boolean hasGameRuleEnum() {
        return HAS_GAME_RULE_ENUM;
    }

    /**
     * Есть Chunk.setForceLoaded (1.14+)
     */
    public static boolean hasForceLoaded() {
        return HAS_FORCE_LOADED;
    }

    /**
     * Есть Adventure API (Paper 1.16.5+)
     */
    public static boolean hasAdventureApi() {
        return HAS_ADVENTURE_API;
    }

    /**
     * Это Folia сервер
     */
    public static boolean isFolia() {
        return HAS_FOLIA;
    }

    /**
     * Это Paper или форк Paper
     */
    public static boolean isPaper() {
        return SERVER_TYPE.equals("Paper") ||
                SERVER_TYPE.equals("Purpur") ||
                SERVER_TYPE.equals("Pufferfish") ||
                SERVER_TYPE.equals("Folia");
    }

    /**
     * Есть PDC - PersistentDataContainer (1.14+)
     */
    public static boolean hasPersistentData() {
        return isAtLeast(14);
    }

    /**
     * Есть Hex цвета в чате (1.16+)
     */
    public static boolean hasHexColors() {
        return isAtLeast(16);
    }

    // ==================== Приватные методы ====================

    private static boolean checkAdventureApi() {
        try {
            Class.forName("net.kyori.adventure.text.Component");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}