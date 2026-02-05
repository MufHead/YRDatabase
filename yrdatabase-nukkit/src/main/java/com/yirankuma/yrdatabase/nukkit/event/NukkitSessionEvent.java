package com.yirankuma.yrdatabase.nukkit.event;

import cn.nukkit.Player;
import cn.nukkit.event.Event;
import com.yirankuma.yrdatabase.api.event.SessionReason;
import com.yirankuma.yrdatabase.api.session.SessionEventData;

/**
 * Base class for Nukkit session events.
 * These events are triggered when player session state changes are detected.
 *
 * <p>Note: Each subclass must define its own static HandlerList and getHandlers() method.</p>
 *
 * @author YiranKuma
 */
public abstract class NukkitSessionEvent extends Event {

    protected final SessionEventData data;
    protected final Player player; // May be null if player is not on this server

    public NukkitSessionEvent(SessionEventData data, Player player) {
        this.data = data;
        this.player = player;
    }

    /**
     * Get the player's UUID string.
     */
    public String getPlayerId() {
        return data.getPlayerId();
    }

    /**
     * Get the player's display name.
     */
    public String getPlayerName() {
        return data.getPlayerName();
    }

    /**
     * Get the session reason.
     */
    public SessionReason getReason() {
        return data.getReason();
    }

    /**
     * Get the event timestamp.
     */
    public long getTimestamp() {
        return data.getTimestamp();
    }

    /**
     * Get the underlying session event data.
     */
    public SessionEventData getData() {
        return data;
    }

    /**
     * Get the Nukkit player object if they are on this server.
     * May return null if the event was received from another server.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Check if the player is on this server.
     */
    public boolean isOnThisServer() {
        return player != null && player.isOnline();
    }
}
