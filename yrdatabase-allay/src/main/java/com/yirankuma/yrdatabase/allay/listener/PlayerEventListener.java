package com.yirankuma.yrdatabase.allay.listener;

import com.yirankuma.yrdatabase.allay.YRDatabaseAllay;
import com.yirankuma.yrdatabase.allay.event.AllayPlayerDataInitEvent;
import com.yirankuma.yrdatabase.allay.event.AllayPlayerDataSaveEvent;
import com.yirankuma.yrdatabase.api.event.SessionReason;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.server.PlayerJoinEvent;
import org.allaymc.api.eventbus.event.server.PlayerQuitEvent;
import org.allaymc.api.player.Player;
import org.allaymc.api.server.Server;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to player events and triggers data initialization/persistence.
 *
 * @author YiranKuma
 */
public class PlayerEventListener {

    private final YRDatabaseAllay plugin;
    private final Set<String> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> joinTimes = new ConcurrentHashMap<>();

    public PlayerEventListener(YRDatabaseAllay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerId = getPlayerId(player);
        String playerName = player.getOriginName();

        onlinePlayers.add(playerId);
        joinTimes.put(playerId, System.currentTimeMillis());

        // Determine session reason
        // In standalone mode, we treat all joins as LOCAL_JOIN
        // In cluster mode with WaterdogPE, this would check against real online players
        SessionReason reason = SessionReason.LOCAL_JOIN;

        // Fire data init event
        AllayPlayerDataInitEvent initEvent = new AllayPlayerDataInitEvent(
                player, playerId, playerName, reason
        );

        // Call the event on Allay's event bus
        Server.getInstance().getEventBus().callEvent(initEvent);

        if (initEvent.shouldLoadData()) {
            plugin.getPluginLogger().debug("Player {} data should be loaded (reason: {})",
                    playerName, reason);
        }
    }

    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String playerId = getPlayerId(player);
        String playerName = player.getOriginName();

        // Determine session reason
        // In standalone mode, all quits are LOCAL_QUIT
        SessionReason reason = SessionReason.LOCAL_QUIT;

        // Fire data save event
        AllayPlayerDataSaveEvent saveEvent = new AllayPlayerDataSaveEvent(
                player, playerId, playerName, reason
        );

        // Call the event on Allay's event bus
        Server.getInstance().getEventBus().callEvent(saveEvent);

        if (saveEvent.shouldPersist() && !saveEvent.isCancelled()) {
            plugin.getPluginLogger().debug("Player {} data should be persisted (reason: {})",
                    playerName, reason);

            // Trigger persistence for all tables associated with this player
            // Other plugins should listen to the save event and persist their data
        }

        // Cleanup
        onlinePlayers.remove(playerId);
        joinTimes.remove(playerId);
    }

    /**
     * Get a unique player ID.
     *
     * @param player Player instance
     * @return Player ID (UUID string)
     */
    private String getPlayerId(Player player) {
        UUID uuid = player.getLoginData().getUuid();
        return uuid != null ? uuid.toString() : player.getOriginName();
    }

    /**
     * Check if a player is online.
     *
     * @param playerId Player ID
     * @return true if online
     */
    public boolean isOnline(String playerId) {
        return onlinePlayers.contains(playerId);
    }

    /**
     * Get the join time of a player.
     *
     * @param playerId Player ID
     * @return Join timestamp, or -1 if not found
     */
    public long getJoinTime(String playerId) {
        return joinTimes.getOrDefault(playerId, -1L);
    }

    /**
     * Persist all online players' data (called on server shutdown).
     */
    public void persistAllPlayers() {
        plugin.getPluginLogger().info("Persisting data for {} online players...", onlinePlayers.size());

        for (String playerId : onlinePlayers) {
            // Fire save event with SERVER_SHUTDOWN reason
            AllayPlayerDataSaveEvent saveEvent = new AllayPlayerDataSaveEvent(
                    null, playerId, "Unknown", SessionReason.SERVER_SHUTDOWN
            );
            Server.getInstance().getEventBus().callEvent(saveEvent);
        }

        onlinePlayers.clear();
        joinTimes.clear();

        plugin.getPluginLogger().info("All player data persisted");
    }
}
