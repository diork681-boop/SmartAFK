package com.honeymysteryworld.smartafk.listeners;

import com.honeymysteryworld.smartafk.AfkManager;
import com.honeymysteryworld.smartafk.SmartAFK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class PlayerActivityListener implements Listener {

    private final SmartAFK plugin;
    private final AfkManager afkManager;

    public PlayerActivityListener(SmartAFK plugin, AfkManager afkManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        afkManager.getAfkPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        afkManager.removePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("detection.movement", true)) return;

        // Игнорируем только поворот головы
        if (event.getTo() == null) return;

        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            afkManager.updateActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("detection.chat", true)) return;

        // Синхронизируем с основным потоком
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                afkManager.updateActivity(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("detection.commands", true)) return;

        String cmd = event.getMessage().toLowerCase();

        // Игнорируем команды плагина
        if (cmd.startsWith("/afk")) return;

        afkManager.updateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("detection.block-break", true)) return;
        afkManager.updateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("detection.block-place", true)) return;
        afkManager.updateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("detection.interact", true)) return;
        afkManager.updateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (!plugin.getConfig().getBoolean("detection.inventory", true)) return;

        if (event.getWhoClicked() instanceof Player) {
            afkManager.updateActivity((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("detection.interact", true)) return;
        afkManager.updateActivity(event.getPlayer());
    }
}