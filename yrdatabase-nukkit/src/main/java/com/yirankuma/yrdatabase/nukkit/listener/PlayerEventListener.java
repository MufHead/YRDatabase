package com.yirankuma.yrdatabase.nukkit.listener;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.nukkit.YRDatabaseNukkit;
import com.yirankuma.yrdatabase.nukkit.session.NukkitSessionBridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Player event listener for NukkitMOT.
 * Handles player join/quit events for session management.
 *
 * <p>This listener:</p>
 * <ul>
 *   <li>Saves/loads player session data to database</li>
 *   <li>Triggers session events via NukkitSessionBridge</li>
 *   <li>In standalone mode, triggers local join/quit events</li>
 *   <li>In proxy mode, waits for Redis confirmation from Waterdog</li>
 * </ul>
 *
 * @author YiranKuma
 */
public class PlayerEventListener implements Listener {

    private final YRDatabaseNukkit plugin;

    public PlayerEventListener(YRDatabaseNukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getLogger().debug("Player " + playerName + " joined, loading data...");

        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) {
            plugin.getLogger().warning("Database not initialized, cannot load player data");
            return;
        }

        // Trigger local join event via session bridge
        // In proxy mode, this will wait for Redis confirmation
        // In standalone mode, this will immediately trigger the event
        NukkitSessionBridge sessionBridge = YRDatabaseNukkit.getSessionBridge();
        if (sessionBridge != null) {
            sessionBridge.triggerLocalJoin(uuid.toString(), playerName);
        }

        // Load player session data asynchronously
        db.get("player_sessions", uuid.toString())
            .thenAccept(result -> {
                if (result.isPresent()) {
                    Map<String, Object> data = result.get();
                    plugin.getLogger().debug("Loaded session data for " + playerName);
                    
                    // Apply session data (e.g., restore previous state)
                    // This runs on async thread, be careful with Nukkit API calls
                } else {
                    // First time player, create new session
                    plugin.getLogger().debug("Creating new session for " + playerName);
                    createNewSession(uuid, playerName);
                }
            })
            .exceptionally(e -> {
                plugin.getLogger().error("Failed to load session for " + playerName, e);
                return null;
            });

        // Record join event
        recordPlayerEvent(uuid, playerName, "JOIN");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getLogger().debug("Player " + playerName + " quit, notifying session bridge...");

        // Trigger local quit event via session bridge
        // In proxy mode, this will wait for Redis confirmation before persisting
        // In standalone mode, this will immediately trigger the event and persist
        NukkitSessionBridge sessionBridge = YRDatabaseNukkit.getSessionBridge();
        if (sessionBridge != null) {
            sessionBridge.triggerLocalQuit(uuid.toString(), playerName);
        }

        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) {
            plugin.getLogger().warning("Database not initialized, cannot save player data");
            return;
        }

        // Save player session data to cache
        // Actual persistence will be handled by session events
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("uuid", uuid.toString());
        sessionData.put("name", playerName);
        sessionData.put("lastSeen", System.currentTimeMillis());
        sessionData.put("lastServer", plugin.getServer().getName());
        
        // Get player stats
        sessionData.put("health", (double) player.getHealth());
        sessionData.put("gamemode", player.getGamemode());
        
        // Position
        sessionData.put("x", player.getX());
        sessionData.put("y", player.getY());
        sessionData.put("z", player.getZ());
        sessionData.put("world", player.getLevel().getName());

        db.set("player_sessions", uuid.toString(), sessionData)
            .thenAccept(success -> {
                if (success) {
                    plugin.getLogger().debug("Cached session data for " + playerName);
                } else {
                    plugin.getLogger().warning("Failed to cache session data for " + playerName);
                }
            })
            .exceptionally(e -> {
                plugin.getLogger().error("Error caching session for " + playerName, e);
                return null;
            });

        // Record quit event
        recordPlayerEvent(uuid, playerName, "QUIT");
    }

    private void createNewSession(UUID uuid, String playerName) {
        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) return;

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("uuid", uuid.toString());
        sessionData.put("name", playerName);
        sessionData.put("firstJoin", System.currentTimeMillis());
        sessionData.put("lastSeen", System.currentTimeMillis());

        db.set("player_sessions", uuid.toString(), sessionData)
            .thenAccept(success -> {
                if (success) {
                    plugin.getLogger().debug("Created new session for " + playerName);
                }
            });
    }

    private void recordPlayerEvent(UUID uuid, String playerName, String eventType) {
        DatabaseManager db = YRDatabaseNukkit.getDatabaseManager();
        if (db == null) return;

        String eventKey = uuid.toString() + "_" + System.currentTimeMillis();
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("uuid", uuid.toString());
        eventData.put("name", playerName);
        eventData.put("event", eventType);
        eventData.put("timestamp", System.currentTimeMillis());
        eventData.put("server", plugin.getServer().getName());

        db.set("player_events", eventKey, eventData)
            .exceptionally(e -> {
                plugin.getLogger().error("Failed to record event " + eventType + " for " + playerName, e);
                return null;
            });
    }
}
