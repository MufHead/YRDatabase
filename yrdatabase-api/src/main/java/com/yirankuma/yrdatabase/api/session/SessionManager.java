package com.yirankuma.yrdatabase.api.session;

/**
 * Session manager interface for managing player session lifecycle.
 * Provides methods for platforms to interact with the session system.
 *
 * @author YiranKuma
 */
public interface SessionManager {

    /**
     * Register a session event listener.
     * 
     * @param listener The listener to register
     */
    void registerListener(SessionEventListener listener);

    /**
     * Unregister a session event listener.
     * 
     * @param listener The listener to unregister
     */
    void unregisterListener(SessionEventListener listener);

    /**
     * Check if the session manager is running in proxy mode.
     * In proxy mode, events are received from Redis Pub/Sub.
     * In standalone mode, events are triggered by local player events.
     *
     * @return true if running in proxy mode
     */
    boolean isProxyMode();

    /**
     * Start the session manager.
     * In proxy mode, this subscribes to Redis channels.
     */
    void start();

    /**
     * Stop the session manager.
     * In proxy mode, this unsubscribes from Redis channels.
     */
    void stop();

    /**
     * Manually trigger a local quit event (for standalone mode).
     * This is called by platform modules when a player quits locally.
     *
     * @param playerId Player's UUID string
     * @param playerName Player's display name
     */
    void triggerLocalQuit(String playerId, String playerName);

    /**
     * Manually trigger a local join event (for standalone mode).
     * This is called by platform modules when a player joins locally.
     *
     * @param playerId Player's UUID string
     * @param playerName Player's display name
     */
    void triggerLocalJoin(String playerId, String playerName);
}
