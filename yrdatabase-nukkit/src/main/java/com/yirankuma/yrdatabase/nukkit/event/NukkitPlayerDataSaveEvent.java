package com.yirankuma.yrdatabase.nukkit.event;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Event;
import com.yirankuma.yrdatabase.api.event.PlayerDataSaveEvent;
import com.yirankuma.yrdatabase.api.event.SessionReason;

/**
 * Nukkit-specific player data save event.
 * Fired when a player quits and their data should be persisted.
 *
 * @author YiranKuma
 */
public class NukkitPlayerDataSaveEvent extends Event implements PlayerDataSaveEvent, Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Player player;
    private final String playerId;
    private final String playerName;
    private final SessionReason reason;
    private final long timestamp;
    private boolean cancelled = false;

    public NukkitPlayerDataSaveEvent(Player player, String playerId, String playerName, SessionReason reason) {
        this.player = player;
        this.playerId = playerId;
        this.playerName = playerName;
        this.reason = reason;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public SessionReason getReason() {
        return reason;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the Nukkit player object.
     *
     * @return Player object
     */
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
