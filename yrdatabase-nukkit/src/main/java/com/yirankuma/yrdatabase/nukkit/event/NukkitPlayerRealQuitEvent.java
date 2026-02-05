package com.yirankuma.yrdatabase.nukkit.event;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import com.yirankuma.yrdatabase.api.session.SessionEventData;

/**
 * Event fired when a player truly quits the proxy network.
 * 
 * <p>In standalone mode, this is fired when a player quits the server.</p>
 * <p>In proxy mode, this is fired when Waterdog confirms the player left the network.</p>
 *
 * <p><b>IMPORTANT:</b> Plugins should persist player data when receiving this event.
 * This event indicates the player has truly left and data should be saved from cache to database.</p>
 *
 * <p>This event is cancellable. If cancelled, YRDatabase will not automatically persist data,
 * and the plugin that cancelled it takes responsibility for data persistence.</p>
 *
 * @author YiranKuma
 */
public class NukkitPlayerRealQuitEvent extends NukkitSessionEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    
    private boolean cancelled = false;

    public NukkitPlayerRealQuitEvent(SessionEventData data, Player player) {
        super(data, player);
    }

    /**
     * Check if data should be persisted.
     * This considers both the session reason and cancellation state.
     */
    public boolean shouldPersist() {
        return !cancelled && data.shouldPersist();
    }

    /**
     * Get the server the player was last on (for proxy mode).
     */
    public String getLastServer() {
        return data.getFromServer();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Alias for setCancelled(true).
     */
    public void setCancelled() {
        this.cancelled = true;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
