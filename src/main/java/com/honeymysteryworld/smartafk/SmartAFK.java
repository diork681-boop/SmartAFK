package com.honeymysteryworld.smartafk;

import com.honeymysteryworld.smartafk.listeners.PlayerActivityListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartAFK extends JavaPlugin implements TabCompleter {

    private static SmartAFK instance;

    private AfkManager afkManager;
    private PlayerActivityListener activityListener;

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Конфиг
            saveDefaultConfig();

            // Инфо о версии
            getLogger().info("Сервер: " + Bukkit.getName() + " " + VersionUtil.getVersionString());

            // Менеджер АФК
            afkManager = new AfkManager(this);

            // Слушатели
            activityListener = new PlayerActivityListener(this, afkManager);
            getServer().getPluginManager().registerEvents(activityListener, this);

            // Tab-complete
            if (getCommand("afk") != null) getCommand("afk").setTabCompleter(this);
            if (getCommand("afkstatus") != null) getCommand("afkstatus").setTabCompleter(this);
            if (getCommand("afkreload") != null) getCommand("afkreload").setTabCompleter(this);

            getLogger().info("SmartAFK v" + getDescription().getVersion() + " успешно загружен!");

        } catch (Exception e) {
            getLogger().severe("Ошибка загрузки плагина: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            // Корректное выключение менеджера
            if (afkManager != null) {
                afkManager.shutdown();
            }
        } catch (Exception e) {
            getLogger().warning("Ошибка при выключении: " + e.getMessage());
        }

        instance = null;
        getLogger().info("SmartAFK выключен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase();

        try {
            switch (cmd) {
                case "afk":
                    return handleAfkCommand(sender, args);
                case "afkstatus":
                    return handleStatusCommand(sender, args);
                case "afkreload":
                    return handleReloadCommand(sender, args);
                default:
                    return false;
            }
        } catch (Exception e) {
            sender.sendMessage(colorize("&cПроизошла ошибка! Проверьте консоль."));
            getLogger().warning("Ошибка команды /" + cmd + ": " + e.getMessage());
            return true;
        }
    }

    private boolean handleAfkCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(colorize(getMessage("messages.players-only", "&cТолько для игроков!")));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("smartafk.afk")) {
            sender.sendMessage(colorize(getMessage("messages.no-permission", "&cНет прав!")));
            return true;
        }

        afkManager.toggleAfk(player);
        return true;
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartafk.status")) {
            sender.sendMessage(colorize(getMessage("messages.no-permission", "&cНет прав!")));
            return true;
        }

        String prefix = getMessage("messages.prefix", "&7[&6SmartAFK&7] ");

        sender.sendMessage(colorize("&6&l══════ АФК Игроки ══════"));

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);
            if (afkPlayer != null && afkPlayer.isAfk()) {
                sender.sendMessage(colorize("&7• &e" + player.getName() +
                        " &8— &7" + afkPlayer.getAfkDurationFormatted()));
                count++;
            }
        }

        if (count == 0) {
            sender.sendMessage(colorize("&7Никто не АФК"));
        } else {
            sender.sendMessage(colorize("&6Всего: &e" + count + " &6игрок(ов)"));
        }

        sender.sendMessage(colorize("&6&l═════════════════════════"));

        return true;
    }

    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartafk.reload")) {
            sender.sendMessage(colorize(getMessage("messages.no-permission", "&cНет прав!")));
            return true;
        }

        // Перезагружаем конфиг
        reloadConfig();

        // Обновляем настройки в менеджере
        if (afkManager != null) {
            afkManager.reloadSettings();
        }

        // Обновляем настройки в листенере
        if (activityListener != null) {
            activityListener.reloadSettings();
        }

        sender.sendMessage(colorize(getMessage("messages.reload", "&aКонфиг успешно перезагружен!")));
        getLogger().info("Конфиг перезагружен игроком " + sender.getName());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Для этих команд не нужен tab-complete
        return Collections.emptyList();
    }

    // ==================== Утилиты ====================

    public static SmartAFK getInstance() {
        return instance;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public String getMessage(String path, String def) {
        return getConfig().getString(path, def);
    }

    public String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}