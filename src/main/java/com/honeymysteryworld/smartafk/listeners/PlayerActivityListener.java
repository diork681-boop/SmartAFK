package com.honeymysteryworld.smartafk.listeners;

import com.honeymysteryworld.smartafk.AfkManager;
import com.honeymysteryworld.smartafk.AfkPlayer;
import com.honeymysteryworld.smartafk.SmartAFK;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class PlayerActivityListener implements Listener {

    private final SmartAFK plugin;
    private final AfkManager afkManager;

    // Кэш настроек (обновляется при reload)
    private boolean detectMovement;
    private boolean detectChat;
    private boolean detectCommands;
    private boolean detectBlockBreak;
    private boolean detectBlockPlace;
    private boolean detectInteract;
    private boolean detectInventory;
    private boolean detectDamage;
    private boolean disableDamage;
    private String afkWorldName;

    public PlayerActivityListener(SmartAFK plugin, AfkManager afkManager) {
        this.plugin = plugin;
        this.afkManager = afkManager;
        reloadSettings();
    }

    /**
     * Перезагрузка настроек из конфига
     */
    public void reloadSettings() {
        detectMovement = plugin.getConfig().getBoolean("detection.movement", true);
        detectChat = plugin.getConfig().getBoolean("detection.chat", true);
        detectCommands = plugin.getConfig().getBoolean("detection.commands", true);
        detectBlockBreak = plugin.getConfig().getBoolean("detection.block-break", true);
        detectBlockPlace = plugin.getConfig().getBoolean("detection.block-place", true);
        detectInteract = plugin.getConfig().getBoolean("detection.interact", true);
        detectInventory = plugin.getConfig().getBoolean("detection.inventory", true);
        detectDamage = plugin.getConfig().getBoolean("detection.damage", true);
        disableDamage = plugin.getConfig().getBoolean("settings.disable-damage", true);
        afkWorldName = plugin.getConfig().getString("afk-world.world-name", "world_afk");
    }

    /**
     * Проверка — игрок в АФК мире?
     */
    private boolean isInAfkWorld(Player player) {
        if (player == null || player.getWorld() == null) return false;
        return player.getWorld().getName().equals(afkWorldName);
    }

    /**
     * Безопасное обновление активности
     */
    private void safeUpdateActivity(Player player) {
        if (player == null || !player.isOnline()) return;

        // Не обновляем активность если игрок в АФК-мире (ещё телепортируется)
        if (isInAfkWorld(player)) return;

        try {
            afkManager.updateActivity(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка обновления активности для " + player.getName() + ": " + e.getMessage());
        }
    }

    // ==================== СОБЫТИЯ ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            afkManager.getAfkPlayer(event.getPlayer());
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при входе игрока: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        try {
            Player player = event.getPlayer();
            AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);

            // Возвращаем на место перед выходом если был АФК
            if (afkPlayer != null && afkPlayer.isAfk() && afkPlayer.getReturnLocation() != null) {
                Location returnLoc = afkPlayer.getReturnLocation();
                if (returnLoc.getWorld() != null) {
                    player.teleport(returnLoc);
                }
            }

            afkManager.removePlayer(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при выходе игрока: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!detectMovement) return;

        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();

        // Проверяем только реальное перемещение (не поворот головы)
        if (from.getBlockX() != to.getBlockX() ||
                from.getBlockY() != to.getBlockY() ||
                from.getBlockZ() != to.getBlockZ()) {
            safeUpdateActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!detectChat) return;

        final Player player = event.getPlayer();

        // Синхронизируем с основным потоком
        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                safeUpdateActivity(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!detectCommands) return;

        String cmd = event.getMessage().toLowerCase();

        // Игнорируем команды плагина
        if (cmd.startsWith("/afk")) return;

        safeUpdateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!detectBlockBreak) return;
        safeUpdateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!detectBlockPlace) return;
        safeUpdateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (!detectInteract) return;
        safeUpdateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (!detectInventory) return;

        if (event.getWhoClicked() instanceof Player) {
            safeUpdateActivity((Player) event.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!detectInteract) return;
        safeUpdateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);

        // Обновляем активность при получении урона
        if (detectDamage) {
            safeUpdateActivity(player);
        }

        // Отменяем урон для АФК игроков
        if (afkPlayer != null && afkPlayer.isAfk() && disableDamage) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Сбрасываем АФК после респавна
        safeUpdateActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        // Не сбрасываем АФК если телепортируемся В афк-мир
        if (event.getTo() != null &&
                event.getTo().getWorld() != null &&
                event.getTo().getWorld().getName().equals(afkWorldName)) {
            return;
        }

        // Сбрасываем АФК при телепорте куда-то ещё
        if (detectMovement) {
            safeUpdateActivity(event.getPlayer());
        }
    }
}