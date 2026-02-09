package com.honeymysteryworld.smartafk.utils;

import com.honeymysteryworld.smartafk.SmartAFK;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {

    private final SmartAFK plugin;
    private final SimpleDateFormat dateFormat;
    private boolean debugEnabled;
    private File logFile;

    public Logger(SmartAFK plugin) {
        this.plugin = plugin;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.debugEnabled = plugin.getConfig().getBoolean("settings.debug", false);

        // Создаём папку для логов
        File logsFolder = new File(plugin.getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }

        // Файл лога на сегодня
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        this.logFile = new File(logsFolder, "smartafk-" + today + ".log");
    }

    public void reload() {
        this.debugEnabled = plugin.getConfig().getBoolean("settings.debug", false);
    }

    public void info(String message) {
        plugin.getLogger().info(message);
        writeToFile("INFO", message);
    }

    public void warning(String message) {
        plugin.getLogger().warning(message);
        writeToFile("WARNING", message);
    }

    public void error(String message) {
        plugin.getLogger().severe(message);
        writeToFile("ERROR", message);
    }

    public void error(String message, Throwable throwable) {
        plugin.getLogger().severe(message + ": " + throwable.getMessage());
        writeToFile("ERROR", message + ": " + throwable.getMessage());

        if (debugEnabled) {
            throwable.printStackTrace();
        }
    }

    public void debug(String message) {
        if (debugEnabled) {
            plugin.getLogger().info("[DEBUG] " + message);
            writeToFile("DEBUG", message);
        }
    }

    private void writeToFile(String level, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            String timestamp = dateFormat.format(new Date());
            writer.println("[" + timestamp + "] [" + level + "] " + message);
        } catch (Exception ignored) {
            // Не падаем если не можем записать лог
        }
    }
}