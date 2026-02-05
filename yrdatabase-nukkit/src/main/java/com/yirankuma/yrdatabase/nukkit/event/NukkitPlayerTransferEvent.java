package com.yirankuma.yrdatabase.nukkit.event;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;
import com.yirankuma.yrdatabase.api.session.SessionEventData;

/**
 * Event fired when a player transfers between servers in a proxy network.
 * 
 * <p><b>IMPORTANT:</b> Plugins should NOT persist data when receiving this event.
 * The player is just moving between servers, and their data should remain in Redis cache
 * for the destination server to access.</p>
 *
 * <p>This event is only fired in proxy mode when a transfer is detected.</p>
 *
 * @author YiranKuma
 */
public class NukkitPlayerTransferEvent extends NukkitSessionEvent {

    private static final HandlerList handlers = new HandlerList();

    public NukkitPlayerTransferEvent(SessionEventData data, Player player) {
        super(data, player);
    }

    /**
     * Get the server the player is transferring from.
     */
    public String getFromServer() {
        return data.getFromServer();
    }

    /**
     * Get the server the player is transferring to.
     */
    public String getToServer() {
        return data.getToServer();
    }

    /**
     * Check if this server is the source of the transfer.
     * Useful for determining if we should do cleanup on this server.
     */
    public boolean isFromThisServer(String thisServerName) {
        return thisServerName != null && thisServerName.equals(data.getFromServer());
    }

    /**
     * Check if this server is the destination of the transfer.
     * Useful for determining if we should prepare to receive the player.
     */
    public boolean isToThisServer(String thisServerName) {
        return thisServerName != null && thisServerName.equals(data.getToServer());
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
