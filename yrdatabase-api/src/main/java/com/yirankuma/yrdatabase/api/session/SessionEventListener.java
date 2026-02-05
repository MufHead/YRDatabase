package com.yirankuma.yrdatabase.api.session;

/**
 * Listener interface for session events.
 * Platform modules implement this to receive session events from Core layer.
 *
 * <p>This is the bridge between Core's SessionEventManager and platform-specific events.</p>
 *
 * @author YiranKuma
 */
public interface SessionEventListener {

    /**
     * Called when a player truly joins the proxy network.
     * In standalone mode, this is called on local player join.
     *
     * @param data Event data
     */
    void onPlayerJoin(SessionEventData data);

    /**
     * Called when a player truly quits the proxy network.
     * In standalone mode, this is called on local player quit.
     * 
     * <p>Plugins should persist player data when receiving this event.</p>
     *
     * @param data Event data
     */
    void onPlayerQuit(SessionEventData data);

    /**
     * Called when a player transfers between servers.
     * 
     * <p>Plugins should NOT persist data when receiving this event,
     * as the data should remain in Redis cache for the destination server.</p>
     *
     * @param data Event data
     */
    void onPlayerTransfer(SessionEventData data);
}
