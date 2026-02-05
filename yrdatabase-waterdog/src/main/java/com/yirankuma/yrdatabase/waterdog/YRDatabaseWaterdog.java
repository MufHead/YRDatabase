package com.yirankuma.yrdatabase.waterdog;

import com.yirankuma.yrdatabase.api.protocol.SessionMessage;
import com.yirankuma.yrdatabase.waterdog.config.WaterdogConfig;
import com.yirankuma.yrdatabase.waterdog.redis.RedisPublisher;
import dev.waterdog.waterdogpe.event.defaults.PlayerDisconnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.PlayerLoginEvent;
import dev.waterdog.waterdogpe.event.defaults.TransferCompleteEvent;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * YRDatabase plugin for WaterdogPE proxy.
 * Manages cross-server player sessions via Redis Pub/Sub.
 * 
 * <p>Architecture: High Cohesion, Low Coupling</p>
 * <ul>
 *   <li>Uses Redis Pub/Sub for reliable cross-server messaging</li>
 *   <li>Maintains local session cache for fast lookups</li>
 *   <li>Sends heartbeats for monitoring</li>
 * </ul>
 *
 * @author YiranKuma
 */
public class YRDatabaseWaterdog extends Plugin {

    private static YRDatabaseWaterdog instance;
    private static final String PLUGIN_CHANNEL = "yrdatabase:session";

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private WaterdogConfig config;
    private RedisPublisher redisPublisher;
    private ScheduledExecutorService scheduler;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("YRDatabase-Waterdog is enabling...");

        // Load configuration
        loadConfiguration();

        // Initialize Redis publisher
        initRedisPublisher();

        // Start heartbeat scheduler
        startHeartbeat();

        // Register event listeners
        registerListeners();

        getLogger().info("YRDatabase-Waterdog enabled! Monitoring player sessions.");
    }

    @Override
    public void onDisable() {
        getLogger().info("YRDatabase-Waterdog is disabling...");

        // Stop heartbeat
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        // Shutdown Redis
        if (redisPublisher != null) {
            redisPublisher.shutdown();
        }

        sessions.clear();
        getLogger().info("YRDatabase-Waterdog disabled!");
    }

    private void loadConfiguration() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, "config.yml");
        
        if (!configFile.exists()) {
            config = new WaterdogConfig();
            saveConfig(configFile);
            getLogger().info("Created default configuration file");
        } else {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(fis);
                config = parseConfig(data);
                getLogger().info("Loaded configuration from " + configFile.getPath());
            } catch (Exception e) {
                getLogger().warn("Failed to load config: " + e.getMessage());
                config = new WaterdogConfig();
            }
        }
    }

    private WaterdogConfig parseConfig(Map<String, Object> data) {
        WaterdogConfig cfg = new WaterdogConfig();
        
        if (data == null) return cfg;

        @SuppressWarnings("unchecked")
        Map<String, Object> redisData = (Map<String, Object>) data.get("redis");
        if (redisData != null) {
            WaterdogConfig.Redis redis = cfg.getRedis();
            redis.setEnabled(getBoolean(redisData, "enabled", true));
            redis.setHost(getString(redisData, "host", "localhost"));
            redis.setPort(getInt(redisData, "port", 6379));
            redis.setPassword(getString(redisData, "password", ""));
            redis.setDatabase(getInt(redisData, "database", 0));
            redis.setTimeout(getInt(redisData, "timeout", 5000));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> heartbeatData = (Map<String, Object>) data.get("heartbeat");
        if (heartbeatData != null) {
            WaterdogConfig.Heartbeat heartbeat = cfg.getHeartbeat();
            heartbeat.setEnabled(getBoolean(heartbeatData, "enabled", true));
            heartbeat.setIntervalSeconds(getInt(heartbeatData, "intervalSeconds", 10));
        }

        cfg.setDebug(getBoolean(data, "debug", false));

        return cfg;
    }

    private void saveConfig(File configFile) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        Map<String, Object> data = new LinkedHashMap<>();
        
        Map<String, Object> redisMap = new LinkedHashMap<>();
        redisMap.put("enabled", config.getRedis().isEnabled());
        redisMap.put("host", config.getRedis().getHost());
        redisMap.put("port", config.getRedis().getPort());
        redisMap.put("password", config.getRedis().getPassword());
        redisMap.put("database", config.getRedis().getDatabase());
        redisMap.put("timeout", config.getRedis().getTimeout());
        data.put("redis", redisMap);
        
        Map<String, Object> heartbeatMap = new LinkedHashMap<>();
        heartbeatMap.put("enabled", config.getHeartbeat().isEnabled());
        heartbeatMap.put("intervalSeconds", config.getHeartbeat().getIntervalSeconds());
        data.put("heartbeat", heartbeatMap);
        
        data.put("debug", config.isDebug());

        try (FileWriter writer = new FileWriter(configFile)) {
            yaml.dump(data, writer);
        } catch (IOException e) {
            getLogger().warn("Failed to save config: " + e.getMessage());
        }
    }

    private void initRedisPublisher() {
        if (!config.getRedis().isEnabled()) {
            getLogger().warn("Redis is disabled. Cross-server messaging will not work.");
            return;
        }

        redisPublisher = new RedisPublisher(config.getRedis(), getLogger());
        redisPublisher.initialize().thenAccept(success -> {
            if (success) {
                getLogger().info("Redis publisher initialized successfully");
            } else {
                getLogger().warn("Redis publisher failed to connect");
            }
        });
    }

    private void startHeartbeat() {
        if (!config.getHeartbeat().isEnabled()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "YRDatabase-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        int interval = config.getHeartbeat().getIntervalSeconds();
        scheduler.scheduleAtFixedRate(() -> {
            if (redisPublisher != null && redisPublisher.isConnected()) {
                int onlineCount = getProxy().getPlayers().size();
                int sessionCount = sessions.size();
                redisPublisher.publishHeartbeat(onlineCount, sessionCount);
                
                if (config.isDebug()) {
                    getLogger().info("Heartbeat sent: online=" + onlineCount + ", sessions=" + sessionCount);
                }
            }
        }, interval, interval, TimeUnit.SECONDS);

        getLogger().info("Heartbeat scheduler started (interval: " + interval + "s)");
    }

    private void registerListeners() {
        // Player joins the proxy network (REAL_JOIN)
        getProxy().getEventManager().subscribe(PlayerLoginEvent.class, event -> {
            ProxiedPlayer player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            long timestamp = System.currentTimeMillis();

            // Create session
            PlayerSession session = new PlayerSession(uuid, playerName, timestamp);
            sessions.put(uuid, session);

            getLogger().info("Player " + playerName + " joined the proxy (REAL_JOIN)");

            // Publish via Redis (primary channel)
            if (redisPublisher != null && redisPublisher.isConnected()) {
                redisPublisher.publishRealJoin(uuid.toString(), playerName, timestamp);
            }

            // Also broadcast via plugin message (backup channel)
            SessionMessage msg = SessionMessage.playerJoin(uuid.toString(), playerName, "proxy");
            broadcastPluginMessage(msg);
        });

        // Player quits the proxy network (REAL_QUIT)
        getProxy().getEventManager().subscribe(PlayerDisconnectedEvent.class, event -> {
            ProxiedPlayer player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            long timestamp = System.currentTimeMillis();

            // Remove session
            PlayerSession session = sessions.remove(uuid);
            if (session != null) {
                long duration = timestamp - session.getJoinTime();
                String lastServer = session.getCurrentServer() != null ? session.getCurrentServer() : "unknown";
                
                getLogger().info("Player " + playerName + " left the proxy (REAL_QUIT) after " + (duration / 1000) + "s");

                // Publish via Redis
                if (redisPublisher != null && redisPublisher.isConnected()) {
                    redisPublisher.publishRealQuit(uuid.toString(), playerName, lastServer, timestamp);
                }

                // Broadcast QUIT message via plugin message
                SessionMessage msg = SessionMessage.playerQuit(uuid.toString(), playerName, lastServer);
                broadcastPluginMessage(msg);
            }
        });

        // Player transfers between servers (SERVER_TRANSFER)
        getProxy().getEventManager().subscribe(TransferCompleteEvent.class, event -> {
            ProxiedPlayer player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            String playerName = player.getName();
            ServerInfo fromServer = event.getSourceServer();
            ServerInfo toServer = event.getTargetServer();
            long timestamp = System.currentTimeMillis();

            // Update session
            PlayerSession session = sessions.get(uuid);
            if (session != null) {
                session.setCurrentServer(toServer.getServerName());
                session.setLastTransfer(timestamp);

                getLogger().info("Player " + playerName + " transferred: " +
                           fromServer.getServerName() + " -> " + toServer.getServerName() + " (SERVER_TRANSFER)");

                // Publish transfer via Redis
                if (redisPublisher != null && redisPublisher.isConnected()) {
                    redisPublisher.publishTransfer(
                        uuid.toString(),
                        playerName,
                        fromServer.getServerName(),
                        toServer.getServerName(),
                        timestamp
                    );
                }

                // Broadcast TRANSFER message via plugin message
                SessionMessage msg = SessionMessage.playerTransfer(
                    uuid.toString(),
                    playerName,
                    fromServer.getServerName(),
                    toServer.getServerName()
                );
                broadcastPluginMessage(msg);
            }
        });
    }

    /**
     * Log session message for debugging purposes.
     * Note: WaterdogPE doesn't support plugin messages like BungeeCord.
     * All cross-server communication is handled via Redis Pub/Sub.
     */
    private void broadcastPluginMessage(SessionMessage message) {
        if (config.isDebug()) {
            getLogger().info("Session message (via Redis): " + message.getType() + 
                " - Player: " + message.getPlayerName());
        }
        // Note: Direct plugin message to downstream servers is not supported in WaterdogPE.
        // All session messages are sent via Redis Pub/Sub (which is already done before this call).
    }

    // ==================== Utility Methods ====================

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        Object val = data.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private int getInt(Map<String, Object> data, String key, int defaultValue) {
        Object val = data.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object val = data.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return defaultValue;
    }

    // ==================== Public API ====================

    public PlayerSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public boolean isRedisConnected() {
        return redisPublisher != null && redisPublisher.isConnected();
    }

    public static YRDatabaseWaterdog getInstance() {
        return instance;
    }

    /**
     * Player session data (immutable identity, mutable state).
     */
    public static class PlayerSession {
        private final UUID playerId;
        private final String playerName;
        private final long joinTime;
        private volatile String currentServer;
        private volatile long lastTransfer;

        public PlayerSession(UUID playerId, String playerName, long joinTime) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.joinTime = joinTime;
            this.lastTransfer = joinTime;
        }

        public UUID getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public long getJoinTime() { return joinTime; }
        public String getCurrentServer() { return currentServer; }
        public long getLastTransfer() { return lastTransfer; }

        public void setCurrentServer(String currentServer) {
            this.currentServer = currentServer;
        }

        public void setLastTransfer(long lastTransfer) {
            this.lastTransfer = lastTransfer;
        }
    }
}
