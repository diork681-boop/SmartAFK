package com.honeymysteryworld.smartafk;

import com.honeymysteryworld.smartafk.listeners.PlayerActivityListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SmartAFK extends JavaPlugin {

    private AfkManager afkManager;

    @Override
    public void onEnable() {
        // Конфиг
        saveDefaultConfig();

        // Инфо о версии
        getLogger().info("Версия сервера: " + VersionUtil.getVersionString());

        // Менеджер АФК
        afkManager = new AfkManager(this);

        // Слушатели
        getServer().getPluginManager().registerEvents(
                new PlayerActivityListener(this, afkManager), this
        );

        getLogger().info("SmartAFK v" + getDescription().getVersion() + " успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Возвращаем всех АФК игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);
            if (afkPlayer.isAfk() && afkPlayer.getReturnLocation() != null) {
                player.teleport(afkPlayer.getReturnLocation());
                player.setAllowFlight(afkPlayer.wasAllowFlight());
                player.setFlying(afkPlayer.wasFlying());
            }
        }

        getLogger().info("SmartAFK выключен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String cmd = command.getName().toLowerCase();

        // /afk
        if (cmd.equals("afk")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(colorize("&cТолько для игроков!"));
                return true;
            }

            Player player = (Player) sender;
            afkManager.toggleAfk(player);
            return true;
        }

        // /afkstatus
        if (cmd.equals("afkstatus")) {
            sender.sendMessage(colorize("&6&l=== АФК Игроки ==="));

            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);
                if (afkPlayer.isAfk()) {
                    sender.sendMessage(colorize("&7- &e" + player.getName() +
                            " &7(" + afkPlayer.getAfkDurationFormatted() + ")"));
                    count++;
                }
            }

            if (count == 0) {
                sender.sendMessage(colorize("&7Никто не АФК"));
            } else {
                sender.sendMessage(colorize("&7Всего: &e" + count));
            }

            return true;
        }

        // /afkreload
        if (cmd.equals("afkreload")) {
            if (!sender.hasPermission("smartafk.reload")) {
                sender.sendMessage(colorize("&cНет прав!"));
                return true;
            }

            reloadConfig();
            sender.sendMessage(colorize(
                    getConfig().getString("messages.reload", "&aКонфиг перезагружен!")));
            return true;
        }

        return false;
    }

    public AfkManager getAfkManager() {
        return afkManager;
    }

    private String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}