package com.honeymysteryworld.smartafk;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

public class AfkPlayer {

    private final UUID uuid;
    private long lastActivity;
    private long afkStartTime;
    private boolean afk;

    // Сохранённое состояние
    private Location returnLocation;
    private boolean wasFlying;
    private boolean wasAllowFlight;
    private GameMode wasGameMode;
    private String originalDisplayName;
    private String originalTabName;

    // Здоровье и голод
    private double wasHealth;
    private int wasFood;
    private float wasSaturation;

    // Падение
    private float wasFallDistance;

    // Вагонетка
    private boolean wasInVehicle;

    // Предупреждения
    private long lastWarningTime;

    public AfkPlayer(UUID uuid) {
        this.uuid = uuid;
        this.lastActivity = System.currentTimeMillis();
        this.afkStartTime = 0;
        this.afk = false;
        this.returnLocation = null;
        this.wasFlying = false;
        this.wasAllowFlight = false;
        this.wasGameMode = null;
        this.originalDisplayName = null;
        this.originalTabName = null;
        this.wasHealth = 20.0;
        this.wasFood = 20;
        this.wasSaturation = 5.0f;
        this.wasFallDistance = 0;
        this.wasInVehicle = false;
        this.lastWarningTime = -1;
    }

    // ==================== UUID ====================

    public UUID getUuid() {
        return uuid;
    }

    // ==================== Активность ====================

    public long getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
        this.lastWarningTime = -1;
    }

    public long getInactiveTime() {
        return System.currentTimeMillis() - lastActivity;
    }

    // ==================== АФК статус ====================

    public boolean isAfk() {
        return afk;
    }

    public void setAfk(boolean afk) {
        this.afk = afk;
        if (afk) {
            this.afkStartTime = System.currentTimeMillis();
        } else {
            this.afkStartTime = 0;
            this.lastWarningTime = -1;
        }
    }

    public long getAfkStartTime() {
        return afkStartTime;
    }

    public long getAfkDuration() {
        if (!afk || afkStartTime == 0) {
            return 0;
        }
        return System.currentTimeMillis() - afkStartTime;
    }

    public String getAfkDurationFormatted() {
        long seconds = getAfkDuration() / 1000;

        if (seconds < 60) {
            return seconds + " сек";
        } else if (seconds < 3600) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins + " мин " + secs + " сек";
        } else {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            return hours + " ч " + mins + " мин";
        }
    }

    // ==================== Локация ====================

    public Location getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(Location location) {
        this.returnLocation = location != null ? location.clone() : null;
    }

    public boolean hasReturnLocation() {
        return returnLocation != null && returnLocation.getWorld() != null;
    }

    // ==================== Полёт ====================

    public boolean wasFlying() {
        return wasFlying;
    }

    public void setWasFlying(boolean wasFlying) {
        this.wasFlying = wasFlying;
    }

    public boolean wasAllowFlight() {
        return wasAllowFlight;
    }

    public void setWasAllowFlight(boolean wasAllowFlight) {
        this.wasAllowFlight = wasAllowFlight;
    }

    // ==================== GameMode ====================

    public GameMode getWasGameMode() {
        return wasGameMode;
    }

    public void setWasGameMode(GameMode gameMode) {
        this.wasGameMode = gameMode;
    }

    // ==================== Имена ====================

    public String getOriginalDisplayName() {
        return originalDisplayName;
    }

    public void setOriginalDisplayName(String name) {
        this.originalDisplayName = name;
    }

    public String getOriginalTabName() {
        return originalTabName;
    }

    public void setOriginalTabName(String name) {
        this.originalTabName = name;
    }

    // ==================== Здоровье и голод ====================

    public double getWasHealth() {
        return wasHealth;
    }

    public void setWasHealth(double wasHealth) {
        this.wasHealth = wasHealth;
    }

    public int getWasFood() {
        return wasFood;
    }

    public void setWasFood(int wasFood) {
        this.wasFood = wasFood;
    }

    public float getWasSaturation() {
        return wasSaturation;
    }

    public void setWasSaturation(float wasSaturation) {
        this.wasSaturation = wasSaturation;
    }

    // ==================== Падение ====================

    public float getWasFallDistance() {
        return wasFallDistance;
    }

    public void setWasFallDistance(float wasFallDistance) {
        this.wasFallDistance = wasFallDistance;
    }

    // ==================== Вагонетка ====================

    public boolean wasInVehicle() {
        return wasInVehicle;
    }

    public void setWasInVehicle(boolean wasInVehicle) {
        this.wasInVehicle = wasInVehicle;
    }

    // ==================== Предупреждения ====================

    public long getLastWarningTime() {
        return lastWarningTime;
    }

    public void setLastWarningTime(long time) {
        this.lastWarningTime = time;
    }

    // ==================== Утилиты ====================

    public void reset() {
        this.afk = false;
        this.afkStartTime = 0;
        this.returnLocation = null;
        this.wasFlying = false;
        this.wasAllowFlight = false;
        this.wasGameMode = null;
        this.originalDisplayName = null;
        this.originalTabName = null;
        this.wasHealth = 20.0;
        this.wasFood = 20;
        this.wasSaturation = 5.0f;
        this.wasFallDistance = 0;
        this.wasInVehicle = false;
        this.lastWarningTime = -1;
        this.lastActivity = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "AfkPlayer{" +
                "uuid=" + uuid +
                ", afk=" + afk +
                ", inactive=" + (getInactiveTime() / 1000) + "s" +
                '}';
    }
}