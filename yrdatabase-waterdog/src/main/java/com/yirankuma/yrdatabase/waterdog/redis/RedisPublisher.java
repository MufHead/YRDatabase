package com.yirankuma.yrdatabase.waterdog.redis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yirankuma.yrdatabase.waterdog.config.WaterdogConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Redis publisher for cross-server messaging.
 * 
 * <p>Responsibilities (High Cohesion):</p>
 * <ul>
 *   <li>Manage Redis connection lifecycle</li>
 *   <li>Publish session events to Redis channels</li>
 *   <li>Handle connection failures gracefully</li>
 * </ul>
 * 
 * <p>Low Coupling: Only depends on config and logger interfaces</p>
 *
 * @author YiranKuma
 */
public class RedisPublisher {

    // Channel names for Pub/Sub
    public static final String CHANNEL_PLAYER_JOIN = "yrdatabase:player:join";
    public static final String CHANNEL_PLAYER_QUIT = "yrdatabase:player:quit";
    public static final String CHANNEL_PLAYER_TRANSFER = "yrdatabase:player:transfer";
    public static final String CHANNEL_HEARTBEAT = "yrdatabase:heartbeat";

    private final WaterdogConfig.Redis config;
    private final Logger logger;
    private final Gson gson;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> asyncCommands;

    private volatile boolean connected = false;

    public RedisPublisher(WaterdogConfig.Redis config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.gson = new GsonBuilder().create();
    }

    /**
     * Initialize Redis connection asynchronously.
     *
     * @return Future completing with connection success status
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                RedisURI.Builder uriBuilder = RedisURI.builder()
                        .withHost(config.getHost())
                        .withPort(config.getPort())
                        .withDatabase(config.getDatabase())
                        .withTimeout(Duration.ofMillis(config.getTimeout()));

                String password = config.getPassword();
                if (password != null && !password.isEmpty()) {
                    uriBuilder.withPassword(password.toCharArray());
                }

                redisClient = RedisClient.create(uriBuilder.build());
                connection = redisClient.connect();
                asyncCommands = connection.async();

                // Test connection with PING
                String pong = asyncCommands.ping().get();
                connected = "PONG".equals(pong);

                if (connected) {
                    logger.info("Redis publisher connected to {}:{}", config.getHost(), config.getPort());
                }

                return connected;
            } catch (Exception e) {
                logger.warn("Failed to connect to Redis: {}", e.getMessage());
                connected = false;
                return false;
            }
        });
    }

    /**
     * Shutdown Redis connection gracefully.
     */
    public void shutdown() {
        connected = false;
        
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                logger.debug("Error closing Redis connection: {}", e.getMessage());
            }
        }
        
        if (redisClient != null) {
            try {
                redisClient.shutdown();
            } catch (Exception e) {
                logger.debug("Error shutting down Redis client: {}", e.getMessage());
            }
        }
        
        logger.info("Redis publisher shutdown complete");
    }

    /**
     * Check if currently connected to Redis.
     */
    public boolean isConnected() {
        return connected && connection != null && connection.isOpen();
    }

    /**
     * Publish player real join event.
     */
    public void publishRealJoin(String uid, String username, long timestamp) {
        if (!isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("username", username);
        data.put("timestamp", timestamp);
        data.put("type", "REAL_JOIN");

        publish(CHANNEL_PLAYER_JOIN, data);
    }

    /**
     * Publish player real quit event.
     */
    public void publishRealQuit(String uid, String username, String lastServer, long timestamp) {
        if (!isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("username", username);
        data.put("lastServer", lastServer);
        data.put("timestamp", timestamp);
        data.put("type", "REAL_QUIT");

        publish(CHANNEL_PLAYER_QUIT, data);
    }

    /**
     * Publish player server transfer event.
     */
    public void publishTransfer(String uid, String username, String fromServer, String toServer, long timestamp) {
        if (!isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("username", username);
        data.put("fromServer", fromServer);
        data.put("toServer", toServer);
        data.put("timestamp", timestamp);
        data.put("type", "SERVER_TRANSFER");

        publish(CHANNEL_PLAYER_TRANSFER, data);
    }

    /**
     * Publish heartbeat message.
     */
    public void publishHeartbeat(int onlineCount, int sessionCount) {
        if (!isConnected()) return;

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", System.currentTimeMillis());
        data.put("online", onlineCount);
        data.put("sessions", sessionCount);
        data.put("type", "HEARTBEAT");

        publish(CHANNEL_HEARTBEAT, data);
    }

    /**
     * Internal publish method.
     */
    private void publish(String channel, Map<String, Object> data) {
        try {
            String json = gson.toJson(data);
            asyncCommands.publish(channel, json).whenComplete((count, error) -> {
                if (error != null) {
                    logger.warn("Failed to publish to {}: {}", channel, error.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warn("Error publishing message to {}: {}", channel, e.getMessage());
        }
    }
}
