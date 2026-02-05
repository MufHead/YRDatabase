package com.yirankuma.yrdatabase.core.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yirankuma.yrdatabase.api.event.SessionReason;
import com.yirankuma.yrdatabase.api.provider.CacheProvider;
import com.yirankuma.yrdatabase.api.session.SessionEventData;
import com.yirankuma.yrdatabase.api.session.SessionEventListener;
import com.yirankuma.yrdatabase.api.session.SessionManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Core implementation of SessionManager.
 * Subscribes to Redis Pub/Sub channels and notifies registered listeners.
 *
 * <p>This class is platform-independent and used by both Nukkit and Allay platforms.</p>
 *
 * @author YiranKuma
 */
@Slf4j
public class SessionEventManagerImpl implements SessionManager {

    // Redis Pub/Sub channel names (same as Waterdog publisher)
    public static final String CHANNEL_PLAYER_JOIN = "yrdatabase:player:join";
    public static final String CHANNEL_PLAYER_QUIT = "yrdatabase:player:quit";
    public static final String CHANNEL_PLAYER_TRANSFER = "yrdatabase:player:transfer";

    private final Supplier<CacheProvider> cacheProviderSupplier;
    private final boolean proxyMode;
    private final Gson gson;
    private final List<SessionEventListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean started = false;

    /**
     * Create a new SessionEventManager.
     *
     * @param cacheProviderSupplier Supplier for the cache provider (lazy loading)
     * @param proxyMode             Whether running in proxy mode (Redis Pub/Sub) or standalone
     */
    public SessionEventManagerImpl(Supplier<CacheProvider> cacheProviderSupplier, boolean proxyMode) {
        this.cacheProviderSupplier = cacheProviderSupplier;
        this.proxyMode = proxyMode;
        this.gson = new GsonBuilder().create();
    }

    @Override
    public void registerListener(SessionEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            log.debug("Registered session event listener: {}", listener.getClass().getSimpleName());
        }
    }

    @Override
    public void unregisterListener(SessionEventListener listener) {
        if (listeners.remove(listener)) {
            log.debug("Unregistered session event listener: {}", listener.getClass().getSimpleName());
        }
    }

    @Override
    public boolean isProxyMode() {
        return proxyMode;
    }

    @Override
    public void start() {
        if (started) {
            log.warn("SessionEventManager already started");
            return;
        }

        if (proxyMode) {
            subscribeToRedisChannels();
        }

        started = true;
        log.info("SessionEventManager started (proxyMode={})", proxyMode);
    }

    @Override
    public void stop() {
        if (!started) {
            return;
        }

        if (proxyMode) {
            unsubscribeFromRedisChannels();
        }

        started = false;
        log.info("SessionEventManager stopped");
    }

    @Override
    public void triggerLocalQuit(String playerId, String playerName) {
        SessionEventData data = SessionEventData.builder()
                .playerId(playerId)
                .playerName(playerName)
                .reason(proxyMode ? SessionReason.SERVER_TRANSFER : SessionReason.LOCAL_QUIT)
                .timestamp(System.currentTimeMillis())
                .build();

        if (!proxyMode) {
            // In standalone mode, local quit means real quit
            notifyQuit(data);
        } else {
            // In proxy mode, wait for Redis confirmation
            // This is just a local trigger, the real event comes from Redis
            log.debug("Local quit triggered for {} in proxy mode, waiting for Redis confirmation", playerName);
        }
    }

    @Override
    public void triggerLocalJoin(String playerId, String playerName) {
        SessionEventData data = SessionEventData.builder()
                .playerId(playerId)
                .playerName(playerName)
                .reason(proxyMode ? SessionReason.REAL_JOIN : SessionReason.LOCAL_JOIN)
                .timestamp(System.currentTimeMillis())
                .build();

        if (!proxyMode) {
            // In standalone mode, local join means real join
            notifyJoin(data);
        } else {
            // In proxy mode, wait for Redis confirmation
            log.debug("Local join triggered for {} in proxy mode, waiting for Redis confirmation", playerName);
        }
    }

    // ==================== Redis Subscription ====================

    private void subscribeToRedisChannels() {
        CacheProvider cacheProvider = cacheProviderSupplier.get();
        if (cacheProvider == null || !cacheProvider.isConnected()) {
            log.warn("Cannot subscribe to Redis channels: CacheProvider not available");
            return;
        }

        // Subscribe to player join channel
        cacheProvider.subscribe(CHANNEL_PLAYER_JOIN, this::handleJoinMessage);
        log.debug("Subscribed to channel: {}", CHANNEL_PLAYER_JOIN);

        // Subscribe to player quit channel
        cacheProvider.subscribe(CHANNEL_PLAYER_QUIT, this::handleQuitMessage);
        log.debug("Subscribed to channel: {}", CHANNEL_PLAYER_QUIT);

        // Subscribe to player transfer channel
        cacheProvider.subscribe(CHANNEL_PLAYER_TRANSFER, this::handleTransferMessage);
        log.debug("Subscribed to channel: {}", CHANNEL_PLAYER_TRANSFER);

        log.info("Subscribed to Redis session channels");
    }

    private void unsubscribeFromRedisChannels() {
        CacheProvider cacheProvider = cacheProviderSupplier.get();
        if (cacheProvider == null || !cacheProvider.isConnected()) {
            return;
        }

        cacheProvider.unsubscribe(CHANNEL_PLAYER_JOIN);
        cacheProvider.unsubscribe(CHANNEL_PLAYER_QUIT);
        cacheProvider.unsubscribe(CHANNEL_PLAYER_TRANSFER);

        log.info("Unsubscribed from Redis session channels");
    }

    // ==================== Message Handlers ====================

    private void handleJoinMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(message, Map.class);

            String playerId = (String) data.get("uid");
            String playerName = (String) data.get("username");
            long timestamp = ((Number) data.getOrDefault("timestamp", System.currentTimeMillis())).longValue();

            SessionEventData eventData = SessionEventData.builder()
                    .playerId(playerId)
                    .playerName(playerName)
                    .reason(SessionReason.REAL_JOIN)
                    .timestamp(timestamp)
                    .build();

            log.debug("Received REAL_JOIN event for player: {}", playerName);
            notifyJoin(eventData);

        } catch (Exception e) {
            log.error("Failed to parse join message: {}", message, e);
        }
    }

    private void handleQuitMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(message, Map.class);

            String playerId = (String) data.get("uid");
            String playerName = (String) data.get("username");
            String lastServer = (String) data.get("lastServer");
            long timestamp = ((Number) data.getOrDefault("timestamp", System.currentTimeMillis())).longValue();

            SessionEventData eventData = SessionEventData.builder()
                    .playerId(playerId)
                    .playerName(playerName)
                    .reason(SessionReason.REAL_QUIT)
                    .timestamp(timestamp)
                    .fromServer(lastServer)
                    .build();

            log.debug("Received REAL_QUIT event for player: {}", playerName);
            notifyQuit(eventData);

        } catch (Exception e) {
            log.error("Failed to parse quit message: {}", message, e);
        }
    }

    private void handleTransferMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(message, Map.class);

            String playerId = (String) data.get("uid");
            String playerName = (String) data.get("username");
            String fromServer = (String) data.get("fromServer");
            String toServer = (String) data.get("toServer");
            long timestamp = ((Number) data.getOrDefault("timestamp", System.currentTimeMillis())).longValue();

            SessionEventData eventData = SessionEventData.builder()
                    .playerId(playerId)
                    .playerName(playerName)
                    .reason(SessionReason.SERVER_TRANSFER)
                    .timestamp(timestamp)
                    .fromServer(fromServer)
                    .toServer(toServer)
                    .build();

            log.debug("Received SERVER_TRANSFER event for player: {} ({} -> {})", 
                    playerName, fromServer, toServer);
            notifyTransfer(eventData);

        } catch (Exception e) {
            log.error("Failed to parse transfer message: {}", message, e);
        }
    }

    // ==================== Listener Notification ====================

    private void notifyJoin(SessionEventData data) {
        for (SessionEventListener listener : listeners) {
            try {
                listener.onPlayerJoin(data);
            } catch (Exception e) {
                log.error("Error in session join listener: {}", e.getMessage(), e);
            }
        }
    }

    private void notifyQuit(SessionEventData data) {
        for (SessionEventListener listener : listeners) {
            try {
                listener.onPlayerQuit(data);
            } catch (Exception e) {
                log.error("Error in session quit listener: {}", e.getMessage(), e);
            }
        }
    }

    private void notifyTransfer(SessionEventData data) {
        for (SessionEventListener listener : listeners) {
            try {
                listener.onPlayerTransfer(data);
            } catch (Exception e) {
                log.error("Error in session transfer listener: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Trigger a server shutdown event.
     * This should be called by the platform plugin when the server is shutting down.
     *
     * @param onlinePlayerIds List of player IDs currently online
     * @param playerNames     Map of player ID to player name
     */
    public void triggerServerShutdown(List<String> onlinePlayerIds, Map<String, String> playerNames) {
        for (String playerId : onlinePlayerIds) {
            String playerName = playerNames.getOrDefault(playerId, "Unknown");
            SessionEventData data = SessionEventData.builder()
                    .playerId(playerId)
                    .playerName(playerName)
                    .reason(SessionReason.SERVER_SHUTDOWN)
                    .timestamp(System.currentTimeMillis())
                    .build();

            notifyQuit(data);
        }
        log.info("Triggered server shutdown events for {} players", onlinePlayerIds.size());
    }
}
