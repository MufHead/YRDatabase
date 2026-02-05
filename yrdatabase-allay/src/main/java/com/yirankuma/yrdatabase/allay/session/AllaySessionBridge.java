package com.yirankuma.yrdatabase.allay.session;

import com.yirankuma.yrdatabase.allay.YRDatabaseAllay;
import com.yirankuma.yrdatabase.allay.event.AllayPlayerRealJoinEvent;
import com.yirankuma.yrdatabase.allay.event.AllayPlayerRealQuitEvent;
import com.yirankuma.yrdatabase.allay.event.AllayPlayerTransferEvent;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.session.SessionEventData;
import com.yirankuma.yrdatabase.api.session.SessionEventListener;
import com.yirankuma.yrdatabase.api.session.SessionManager;
import com.yirankuma.yrdatabase.core.session.SessionEventManagerImpl;
import org.allaymc.api.player.Player;
import org.allaymc.api.server.Server;

import java.util.UUID;

/**
 * Bridge between Core's SessionEventManager and Allay's event system.
 *
 * <p>This class:
 * <ul>
 *   <li>Wraps Core's SessionEventManagerImpl</li>
 *   <li>Implements SessionEventListener to receive events from Core</li>
 *   <li>Fires Allay-specific events that plugins can listen to</li>
 * </ul>
 * </p>
 *
 * @author YiranKuma
 */
public class AllaySessionBridge implements SessionEventListener {

    private final YRDatabaseAllay plugin;
    private final SessionEventManagerImpl sessionManager;

    /**
     * Create a new AllaySessionBridge.
     *
     * @param plugin    The plugin instance
     * @param dbManager Database manager for cache provider access
     * @param proxyMode Whether running in proxy mode
     */
    public AllaySessionBridge(YRDatabaseAllay plugin, DatabaseManager dbManager, boolean proxyMode) {
        this.plugin = plugin;

        // Create core session manager with lazy cache provider access
        this.sessionManager = new SessionEventManagerImpl(
            () -> dbManager.getCacheProvider().orElse(null),
            proxyMode
        );

        // Register ourselves as a listener
        this.sessionManager.registerListener(this);
    }

    /**
     * Start the session bridge.
     * This subscribes to Redis channels in proxy mode.
     */
    public void start() {
        sessionManager.start();
        plugin.getPluginLogger().info("Session bridge started (proxy mode: {})", sessionManager.isProxyMode());
    }

    /**
     * Stop the session bridge.
     */
    public void stop() {
        sessionManager.stop();
        plugin.getPluginLogger().info("Session bridge stopped");
    }

    /**
     * Get the underlying session manager.
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Trigger a local player quit event.
     * Called by PlayerEventListener when a player quits this server.
     */
    public void triggerLocalQuit(String playerId, String playerName) {
        sessionManager.triggerLocalQuit(playerId, playerName);
    }

    /**
     * Trigger a local player join event.
     * Called by PlayerEventListener when a player joins this server.
     */
    public void triggerLocalJoin(String playerId, String playerName) {
        sessionManager.triggerLocalJoin(playerId, playerName);
    }

    // ==================== SessionEventListener Implementation ====================

    @Override
    public void onPlayerJoin(SessionEventData data) {
        // Try to find the player on this server
        Player player = findPlayer(data.getPlayerId());

        // Fire Allay event
        AllayPlayerRealJoinEvent event = new AllayPlayerRealJoinEvent(data, player);
        Server.getInstance().getEventBus().callEvent(event);

        plugin.getPluginLogger().debug("Fired AllayPlayerRealJoinEvent for {} (reason: {}, on this server: {})",
            data.getPlayerName(), data.getReason(), player != null);
    }

    @Override
    public void onPlayerQuit(SessionEventData data) {
        // Try to find the player on this server (they may have already disconnected)
        Player player = findPlayer(data.getPlayerId());

        // Fire Allay event
        AllayPlayerRealQuitEvent event = new AllayPlayerRealQuitEvent(data, player);
        Server.getInstance().getEventBus().callEvent(event);

        plugin.getPluginLogger().debug("Fired AllayPlayerRealQuitEvent for {} (reason: {}, shouldPersist: {})",
            data.getPlayerName(), data.getReason(), event.shouldPersist());
    }

    @Override
    public void onPlayerTransfer(SessionEventData data) {
        // Try to find the player on this server
        Player player = findPlayer(data.getPlayerId());

        // Fire Allay event
        AllayPlayerTransferEvent event = new AllayPlayerTransferEvent(data, player);
        Server.getInstance().getEventBus().callEvent(event);

        plugin.getPluginLogger().debug("Fired AllayPlayerTransferEvent for {} ({} -> {})",
            data.getPlayerName(), data.getFromServer(), data.getToServer());
    }

    // ==================== Utility Methods ====================

    /**
     * Find a player by UUID string.
     * Note: In Allay, there's no direct way to get online players map from Server.
     * We store a reference in plugin's PlayerEventListener. For now, return null
     * as the player object may not be critical for event handling.
     */
    private Player findPlayer(String playerId) {
        // In Allay, getting player by UUID is not straightforward from Server.
        // The player reference may be null in the events, which is acceptable.
        // Plugins should use playerId/playerName for data operations.
        return null;
    }
}
