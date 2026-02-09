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
import org.bukkit.command.PluginCommand;
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

    private boolean fullyLoaded = false; // Флаг успешной загрузки

    private static final int BSTATS_ID = 12345; // Замени на свой ID

    @Override
    public void onEnable() {
        instance = this;

        try {
            // Конфиг
            saveDefaultConfig();

            // Логгер (инициализируем первым для логирования ошибок)
            logger = new Logger(this);

            // Валидация конфига
            configValidator = new ConfigValidator(this, logger);
            if (!configValidator.validate()) {
                logger.warning("Конфиг содержал ошибки и был исправлен");
            }

            // Бэкап менеджер
            backupManager = new BackupManager(this, logger);

            // Менеджер АФК (создаёт мир при инициализации — FIX #2)
            afkManager = new AfkManager(this, logger, backupManager);

            // Загружаем бэкап если есть
            backupManager.loadBackup(afkManager.getPlayers());

            // Слушатели
            activityListener = new PlayerActivityListener(this, afkManager);
            getServer().getPluginManager().registerEvents(activityListener, this);

            // Регистрация команд с проверкой (FIX #3)
            registerCommand("afk");
            registerCommand("afkstatus");
            registerCommand("afkreload");

            // bStats
            if (getConfig().getBoolean("settings.metrics", true)) {
                try {
                    new Metrics(this, BSTATS_ID);
                    logger.debug("bStats метрики включены");
                } catch (Exception e) {
                    logger.warning("Не удалось инициализировать bStats: " + e.getMessage());
                }
            }

            // FIX #1: Асинхронное автосохранение бэкапа каждые 5 минут
            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    if (afkManager != null) {
                        backupManager.saveBackup(afkManager.getPlayers());
                    }
                } catch (Exception e) {
                    logger.error("Ошибка автосохранения бэкапа", e);
                }
            }, 6000L, 6000L);

            fullyLoaded = true;

            logger.info("SmartAFK v" + getDescription().getVersion() + " загружен!");
            logger.info("Сервер: " + VersionUtil.getFullVersion());

        } catch (Exception e) {
            if (logger != null) {
                logger.error("Критическая ошибка загрузки плагина", e);
            } else {
                getLogger().severe("Критическая ошибка загрузки: " + e.getMessage());
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (afkManager != null) {
                // Синхронный бэкап при выключении (важно сохранить данные)
                if (backupManager != null) {
                    backupManager.saveBackup(afkManager.getPlayers());
                }
                afkManager.shutdown();
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.error("Ошибка при выключении", e);
            } else {
                getLogger().severe("Ошибка при выключении: " + e.getMessage());
            }
        }

        instance = null;
        fullyLoaded = false;

        if (logger != null) {
            logger.info("SmartAFK выключен!");
        }
    }

    /**
     * FIX #3: Регистрация команды с проверкой
     */
    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);

        if (command == null) {
            logger.error("Команда /" + name + " не найдена в plugin.yml!");
            return;
        }

        command.setTabCompleter(this);
        logger.debug("Команда /" + name + " зарегистрирована");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // FIX #4: Проверка что плагин полностью загружен
        if (!fullyLoaded) {
            sender.sendMessage(colorize("&cПлагин ещё загружается, подождите..."));
            return true;
        }

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
            if (logger != null) {
                logger.error("Ошибка команды /" + cmd, e);
            }
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

        if (afkManager != null) {
            afkManager.toggleAfk(player);
            if (logger != null) {
                logger.debug("Игрок " + player.getName() + " переключил АФК");
            }
        }

        return true;
    }

    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartafk.status")) {
            sender.sendMessage(colorize(getMessage("messages.no-permission", "&cНет прав!")));
            return true;
        }

        sender.sendMessage(colorize("&6&l══════ АФК Игроки ══════"));

        int count = 0;

        if (afkManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);
                if (afkPlayer != null && afkPlayer.isAfk()) {
                    sender.sendMessage(colorize("&7• &e" + player.getName() +
                            " &8— &7" + afkPlayer.getAfkDurationFormatted()));
                    count++;
                }
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

        try {
            // Перезагружаем конфиг
            reloadConfig();

            // Валидация
            if (configValidator != null) {
                configValidator.validate();
            }

            // FIX #4: Проверки на null перед вызовом
            if (logger != null) {
                logger.reload();
            }
            if (backupManager != null) {
                backupManager.reload();
            }
            if (afkManager != null) {
                afkManager.reloadSettings();
            }
            if (activityListener != null) {
                activityListener.reloadSettings();
            }

            sender.sendMessage(colorize(getMessage("messages.reload", "&aКонфиг перезагружен!")));

            if (logger != null) {
                logger.info("Конфиг перезагружен игроком " + sender.getName());
            }

        } catch (Exception e) {
            sender.sendMessage(colorize("&cОшибка перезагрузки! Проверьте консоль."));
            if (logger != null) {
                logger.error("Ошибка перезагрузки конфига", e);
            }
        }

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

    public boolean isFullyLoaded() {
        return fullyLoaded;
    }

    public String getMessage(String path, String def) {
        return getConfig().getString(path, def);
    }

    public String colorize(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}