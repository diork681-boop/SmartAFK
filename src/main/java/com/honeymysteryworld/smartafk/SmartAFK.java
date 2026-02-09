package com.honeymysteryworld.smartafk;

import com.honeymysteryworld.smartafk.listeners.PlayerActivityListener;
import com.honeymysteryworld.smartafk.utils.BackupManager;
import com.honeymysteryworld.smartafk.utils.ConfigValidator;
import com.honeymysteryworld.smartafk.utils.Logger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class SmartAFK extends JavaPlugin implements TabCompleter {

    private static SmartAFK instance;

    private AfkManager afkManager;
    private PlayerActivityListener activityListener;
    private Logger logger;
    private ConfigValidator configValidator;
    private BackupManager backupManager;

    private static final int BSTATS_ID = 12345; // Замени на свой ID

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Конфиг
            saveDefaultConfig();

            // Логгер
            logger = new Logger(this);

            // Валидация конфига
            configValidator = new ConfigValidator(this, logger);
            configValidator.validate();

            // Бэкап менеджер
            backupManager = new BackupManager(this, logger);

            // Менеджер АФК
            afkManager = new AfkManager(this, logger, backupManager);

            // Загружаем бэкап если есть
            backupManager.loadBackup(afkManager.getPlayers());

            // Слушатели
            activityListener = new PlayerActivityListener(this, afkManager);
            getServer().getPluginManager().registerEvents(activityListener, this);

            // Tab-complete
            if (getCommand("afk") != null) getCommand("afk").setTabCompleter(this);
            if (getCommand("afkstatus") != null) getCommand("afkstatus").setTabCompleter(this);
            if (getCommand("afkreload") != null) getCommand("afkreload").setTabCompleter(this);

            // bStats
            if (getConfig().getBoolean("settings.metrics", true)) {
                new Metrics(this, BSTATS_ID);
                logger.debug("bStats метрики включены");
            }

            // Автосохранение бэкапа каждые 5 минут
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                backupManager.saveBackup(afkManager.getPlayers());
            }, 6000L, 6000L);

            logger.info("SmartAFK v" + getDescription().getVersion() + " загружен!");
            logger.info("Сервер: " + VersionUtil.getFullVersion());

        } catch (Exception e) {
            getLogger().severe("Ошибка загрузки плагина: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (afkManager != null) {
                // Сохраняем бэкап перед выключением
                if (backupManager != null) {
                    backupManager.saveBackup(afkManager.getPlayers());
                }
                afkManager.shutdown();
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Ошибка при выключении", e);
            }
        }

        instance = null;
        if (logger != null) {
            logger.info("SmartAFK выключен!");
        }
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
            logger.error("Ошибка команды /" + cmd, e);
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
        logger.debug("Игрок " + player.getName() + " переключил АФК");
        return true;
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartafk.status")) {
            sender.sendMessage(colorize(getMessage("messages.no-permission", "&cНет прав!")));
            return true;
        }

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

        // Валидация
        configValidator.validate();

        // Обновляем все компоненты
        if (logger != null) logger.reload();
        if (backupManager != null) backupManager.reload();
        if (afkManager != null) afkManager.reloadSettings();
        if (activityListener != null) activityListener.reloadSettings();

        sender.sendMessage(colorize(getMessage("messages.reload", "&aКонфиг перезагружен!")));
        logger.info("Конфиг перезагружен игроком " + sender.getName());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    // ==================== Утилиты ====================

    public static SmartAFK getInstance() {
        return instance;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    public Logger getPluginLogger() {
        return logger;
    }

    public String getMessage(String path, String def) {
        return getConfig().getString(path, def);
    }

    public String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}