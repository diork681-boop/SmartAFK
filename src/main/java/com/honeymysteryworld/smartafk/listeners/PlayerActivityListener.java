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

    // Кэш настроек
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

    private boolean isInAfkWorld(Player player) {
        if (player == null || player.getWorld() == null) return false;
        return player.getWorld().getName().equals(afkWorldName);
    }

    /**
     * FIX #1: Убрана проверка isInAfkWorld — теперь игрок может "проснуться"
     * из АФК мира любым действием
     */
    private void safeUpdateActivity(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            afkManager.updateActivity(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка обновления активности: " + e.getMessage());
        }
    }

    // ==================== БЛОКИРОВКА ДВИЖЕНИЯ В АФК ====================

    /**
     * FIX #3: Исправлено дёрганье камеры + разрешаем движение для выхода из АФК
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAfkMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);

        if (afkPlayer == null || !afkPlayer.isAfk()) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Проверяем реальное перемещение (не только поворот головы)
        boolean hasMoved = from.getX() != to.getX() ||
                from.getY() != to.getY() ||
                from.getZ() != to.getZ();

        if (hasMoved) {
            // Игрок пытается двигаться — выводим из АФК
            safeUpdateActivity(player);

            // Если всё ещё в АФК (например, ошибка) — блокируем движение
            // FIX: Используем clone() чтобы не модифицировать оригинал
            if (afkPlayer.isAfk()) {
                Location safeLoc = from.clone();
                safeLoc.setDirection(to.getDirection());
                event.setTo(safeLoc);
            }
        }
    }

    // ==================== СОБЫТИЯ ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);

            // Проверяем — если игрок в АФК мире, но не в статусе АФК (после краша)
            if (isInAfkWorld(player) && (afkPlayer == null || !afkPlayer.isAfk())) {
                // Телепортируем на спавн
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && isInAfkWorld(player)) {
                        player.teleport(plugin.getServer().getWorlds().get(0).getSpawnLocation());
                        plugin.getLogger().info("Игрок " + player.getName() + " был в АФК мире без статуса — телепортирован на спавн");
                    }
                }, 5L);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при входе игрока: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        try {
            afkManager.handlePlayerQuit(event.getPlayer());
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

        // Проверяем реальное перемещение между блоками
        if (from.getBlockX() != to.getBlockX() ||
                from.getBlockY() != to.getBlockY() ||
                from.getBlockZ() != to.getBlockZ()) {
            safeUpdateActivity(event.getPlayer());
        }
    }

    /**
     * FIX #5: Используем лямбду вместо анонимного класса
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!detectChat) return;

        final Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTask(plugin, () -> safeUpdateActivity(player));
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

        if (detectDamage) {
            safeUpdateActivity(player);
        }

        if (afkPlayer != null && afkPlayer.isAfk() && disableDamage) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        safeUpdateActivity(event.getPlayer());
    }

    /**
     * FIX #4: Исправлена обработка телепортации
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null || to.getWorld() == null) return;

        // Если телепортируемся В афк мир — игнорируем
        if (to.getWorld().getName().equals(afkWorldName)) {
            return;
        }

        // Если телепортируемся ИЗ афк мира — снимаем АФК статус
        if (isInAfkWorld(player)) {
            // Используем отложенную задачу, чтобы телепорт успел завершиться
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    safeUpdateActivity(player);
                }
            }, 1L);
            return;
        }

        // Обычный телепорт — обновляем активность
        if (detectMovement) {
            safeUpdateActivity(player);
        }
    }

    /**
     * Дополнительная защита: если игрок как-то застрял в АФК мире
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        // Если вышел из АФК мира — точно снимаем статус
        if (event.getFrom().getName().equals(afkWorldName)) {
            AfkPlayer afkPlayer = afkManager.getAfkPlayer(player);
            if (afkPlayer != null && afkPlayer.isAfk()) {
                safeUpdateActivity(player);
            }
        }
    }
}