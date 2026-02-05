package com.yirankuma.yrdatabase.nukkit.session;

import cn.nukkit.Player;
import cn.nukkit.Server;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.provider.CacheProvider;
import com.yirankuma.yrdatabase.api.session.SessionEventData;
import com.yirankuma.yrdatabase.api.session.SessionEventListener;
import com.yirankuma.yrdatabase.api.session.SessionManager;
import com.yirankuma.yrdatabase.core.session.SessionEventManagerImpl;
import com.yirankuma.yrdatabase.nukkit.YRDatabaseNukkit;
import com.yirankuma.yrdatabase.nukkit.event.NukkitPlayerRealJoinEvent;
import com.yirankuma.yrdatabase.nukkit.event.NukkitPlayerRealQuitEvent;
import com.yirankuma.yrdatabase.nukkit.event.NukkitPlayerTransferEvent;

import java.util.UUID;

/**
 * Bridge between Core's SessionEventManager and Nukkit's event system.
 * 
 * <p>This class:
 * <ul>
 *   <li>Wraps Core's SessionEventManagerImpl</li>
 *   <li>Implements SessionEventListener to receive events from Core</li>
 *   <li>Fires Nukkit-specific events that plugins can listen to</li>
 * </ul>
 * </p>
 *
 * @author YiranKuma
 */
public class NukkitSessionBridge implements SessionEventListener {

    private final YRDatabaseNukkit plugin;
    private final SessionEventManagerImpl sessionManager;
    private final Server server;

    /**
     * Create a new NukkitSessionBridge.
     *
     * @param plugin      The plugin instance
     * @param dbManager   Database manager for cache provider access
     * @param proxyMode   Whether running in proxy mode
     */
    public NukkitSessionBridge(YRDatabaseNukkit plugin, DatabaseManager dbManager, boolean proxyMode) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        
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
        plugin.getLogger().info("Session bridge started (proxy mode: " + sessionManager.isProxyMode() + ")");
    }

    /**
     * Stop the session bridge.
     */
    public void stop() {
        sessionManager.stop();
        plugin.getLogger().info("Session bridge stopped");
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
        
        // Fire Nukkit event
        NukkitPlayerRealJoinEvent event = new NukkitPlayerRealJoinEvent(data, player);
        server.getPluginManager().callEvent(event);
        
        plugin.getLogger().debug("Fired NukkitPlayerRealJoinEvent for " + data.getPlayerName() + 
            " (reason: " + data.getReason() + ", on this server: " + (player != null) + ")");
    }

    @Override
    public void onPlayerQuit(SessionEventData data) {
        // Try to find the player on this server (they may have already disconnected)
        Player player = findPlayer(data.getPlayerId());
        
        // Fire Nukkit event
        NukkitPlayerRealQuitEvent event = new NukkitPlayerRealQuitEvent(data, player);
        server.getPluginManager().callEvent(event);
        
        plugin.getLogger().debug("Fired NukkitPlayerRealQuitEvent for " + data.getPlayerName() + 
            " (reason: " + data.getReason() + ", shouldPersist: " + event.shouldPersist() + ")");
    }

    @Override
    public void onPlayerTransfer(SessionEventData data) {
        // Try to find the player on this server
        Player player = findPlayer(data.getPlayerId());
        
        // Fire Nukkit event
        NukkitPlayerTransferEvent event = new NukkitPlayerTransferEvent(data, player);
        server.getPluginManager().callEvent(event);
        
        plugin.getLogger().debug("Fired NukkitPlayerTransferEvent for " + data.getPlayerName() + 
            " (" + data.getFromServer() + " -> " + data.getToServer() + ")");
    }

    // ==================== Utility Methods ====================

    /**
     * Find a player by UUID string.
     */
    private Player findPlayer(String playerId) {
        try {
            UUID uuid = UUID.fromString(playerId);
            return server.getOnlinePlayers().values().stream()
                .filter(p -> p.getUniqueId().equals(uuid))
                .findFirst()
                .orElse(null);
        } catch (IllegalArgumentException e) {
            // Invalid UUID, try by name
            return server.getPlayer(playerId);
        }
    }
}
