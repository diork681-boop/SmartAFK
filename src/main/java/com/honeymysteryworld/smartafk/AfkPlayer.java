package com.honeymysteryworld.smartafk;

import org.bukkit.Location;
import java.util.UUID;

public class AfkPlayer {

    private final UUID uuid;
    private long lastActivity;
    private boolean afk;
    private Location returnLocation;
    private boolean wasFlying;
    private boolean wasAllowFlight;

    public AfkPlayer(UUID uuid) {
        this.uuid = uuid;
        this.lastActivity = System.currentTimeMillis();
        this.afk = false;
        this.returnLocation = null;
        this.wasFlying = false;
        this.wasAllowFlight = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public long getLastActivity() {
        return lastActivity;
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isAfk() {
        return afk;
    }

    public void setAfk(boolean afk) {
        this.afk = afk;
    }

    public Location getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(Location location) {
        this.returnLocation = location != null ? location.clone() : null;
    }

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

    public long getAfkDuration() {
        return System.currentTimeMillis() - lastActivity;
    }

    public String getAfkDurationFormatted() {
        long seconds = getAfkDuration() / 1000;

        if (seconds < 60) {
            return seconds + " сек";
        } else if (seconds < 3600) {
            return (seconds / 60) + " мин";
        } else {
            return (seconds / 3600) + " ч " + ((seconds % 3600) / 60) + " мин";
        }
    }
}