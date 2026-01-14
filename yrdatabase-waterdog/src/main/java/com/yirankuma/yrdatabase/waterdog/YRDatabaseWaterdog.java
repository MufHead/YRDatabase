package com.yirankuma.yrdatabase.waterdog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yirankuma.yrdatabase.waterdog.config.WaterdogConfig;
import dev.waterdog.waterdogpe.event.defaults.PlayerDisconnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.PlayerLoginEvent;
import dev.waterdog.waterdogpe.event.defaults.ServerTransferRequestEvent;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.plugin.Plugin;
import dev.waterdog.waterdogpe.event.EventPriority;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * YRDatabase WaterdogPE代理端插件
 *
 * 职责:
 * 1. 监听玩家真实加入代理（PlayerLoginEvent）
 * 2. 监听玩家真实退出代理（PlayerDisconnectedEvent）
 * 3. 通过Redis Pub/Sub通知所有Nukkit子服
 *
 * 通信方式: Redis Pub/Sub
 */
public class YRDatabaseWaterdog extends Plugin {

    private Gson gson;
    private WaterdogConfig config;
    private final Map<Long, Long> playerSessions = new ConcurrentHashMap<>();
    private ScheduledExecutorService heartbeatScheduler;

    // Redis连接
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private RedisCommands<String, String> redisCommands;

    @Override
    public void onEnable() {
        gson = new GsonBuilder().create();
        getLogger().info("YRDatabase-Waterdog 正在启动...");

        // 加载配置
        loadPluginConfig();

        // 初始化Redis
        if (config.getRedis().isEnabled()) {
            initRedis();
        } else {
            getLogger().warn("Redis已禁用，将仅记录日志，不会通知Nukkit子服");
        }

        registerListeners();
        startHeartbeat();
        getLogger().info("YRDatabase-Waterdog 已成功启动!");
    }

    @Override
    public void onDisable() {
        getLogger().info("YRDatabase-Waterdog 正在关闭...");

        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdown();
        }

        // 关闭Redis连接
        if (redisConnection != null) {
            redisConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }

        playerSessions.clear();
        getLogger().info("YRDatabase-Waterdog 已关闭");
    }

    private void loadPluginConfig() {
        File configFile = new File(getDataFolder(), "config.json");

        // 如果配置文件不存在，创建默认配置
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            saveResource("config.json");
            getLogger().info("已生成默认配置文件: " + configFile.getPath());
        }

        // 加载配置
        try (FileReader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, WaterdogConfig.class);

            if (config == null) {
                config = new WaterdogConfig();
            }

            getLogger().info("配置文件加载成功");
            getLogger().info("Redis: " + (config.getRedis().isEnabled() ? "已启用" : "已禁用"));

        } catch (Exception e) {
            getLogger().error("加载配置文件失败，使用默认配置", e);
            config = new WaterdogConfig();
        }
    }

    private void initRedis() {
        try {
            WaterdogConfig.Redis redisConfig = config.getRedis();

            // 构建Redis URI
            RedisURI.Builder uriBuilder = RedisURI.Builder
                    .redis(redisConfig.getHost(), redisConfig.getPort())
                    .withDatabase(redisConfig.getDatabase())
                    .withTimeout(java.time.Duration.ofMillis(redisConfig.getTimeout()));

            if (redisConfig.getPassword() != null && !redisConfig.getPassword().isEmpty()) {
                uriBuilder.withPassword(redisConfig.getPassword().toCharArray());
            }

            RedisURI redisUri = uriBuilder.build();

            // 创建Redis客户端
            redisClient = RedisClient.create(redisUri);
            redisConnection = redisClient.connect();
            redisCommands = redisConnection.sync();

            // 测试连接
            String pong = redisCommands.ping();
            if ("PONG".equals(pong)) {
                getLogger().info("Redis连接成功: " + redisConfig.getHost() + ":" + redisConfig.getPort());
            }

        } catch (Exception e) {
            getLogger().error("Redis连接失败，将仅记录日志", e);
            redisClient = null;
            redisConnection = null;
            redisCommands = null;
        }
    }

    private void registerListeners() {
        getProxy().getEventManager().subscribe(PlayerLoginEvent.class, this::onPlayerLogin, EventPriority.NORMAL);
        getProxy().getEventManager().subscribe(PlayerDisconnectedEvent.class, this::onPlayerDisconnect, EventPriority.NORMAL);
        getProxy().getEventManager().subscribe(ServerTransferRequestEvent.class, this::onServerTransfer, EventPriority.NORMAL);
        getLogger().info("事件监听器已注册");
    }

    private void onPlayerLogin(PlayerLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        long uid = getPlayerUid(player);
        String username = player.getName();

        getLogger().info("玩家真实加入: " + username + " (UID: " + uid + ")");
        playerSessions.put(uid, System.currentTimeMillis());

        // 通过Redis发布真实加入消息
        publishRealJoin(uid, username);
    }

    private void onPlayerDisconnect(PlayerDisconnectedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        long uid = getPlayerUid(player);
        String username = player.getName();
        String lastServer = player.getServerInfo() != null ? player.getServerInfo().getServerName() : "unknown";

        getLogger().info("玩家真实退出: " + username + " (UID: " + uid + ")");
        playerSessions.remove(uid);

        // 通过Redis发布真实退出消息
        publishRealQuit(uid, username, lastServer);
    }

    private void onServerTransfer(ServerTransferRequestEvent event) {
        ProxiedPlayer player = event.getPlayer();
        long uid = getPlayerUid(player);
        String fromServer = player.getServerInfo() != null ? player.getServerInfo().getServerName() : "unknown";
        String toServer = event.getTargetServer().getServerName();

        getLogger().info("玩家转服: " + player.getName() + " (UID: " + uid + ") " + fromServer + " -> " + toServer);

        // 转服事件不发布消息，Nukkit子服不应触发持久化
    }

    private void startHeartbeat() {
        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            int onlineCount = getProxy().getPlayers().size();
            getLogger().debug("心跳: 在线玩家=" + onlineCount + ", 会话数=" + playerSessions.size());

            // 定期发布心跳（可选）
            if (redisCommands != null) {
                try {
                    String heartbeatData = gson.toJson(Map.of(
                            "timestamp", System.currentTimeMillis(),
                            "online", onlineCount,
                            "sessions", playerSessions.size()
                    ));
                    redisCommands.publish("yrdatabase:heartbeat", heartbeatData);
                } catch (Exception e) {
                    getLogger().warn("发布心跳失败: " + e.getMessage());
                }
            }
        }, 10, 10, TimeUnit.SECONDS);
        getLogger().info("心跳任务已启动");
    }

    /**
     * 发布玩家真实加入消息
     */
    private void publishRealJoin(long uid, String username) {
        if (redisCommands == null) {
            return;
        }

        try {
            String message = gson.toJson(Map.of(
                    "uid", uid,
                    "username", username,
                    "timestamp", System.currentTimeMillis(),
                    "type", "REAL_JOIN"
            ));

            long receivers = redisCommands.publish("yrdatabase:player:join", message);
            getLogger().debug("已发布REAL_JOIN消息，接收者数量: " + receivers);

        } catch (Exception e) {
            getLogger().error("发布REAL_JOIN消息失败", e);
        }
    }

    /**
     * 发布玩家真实退出消息
     */
    private void publishRealQuit(long uid, String username, String lastServer) {
        if (redisCommands == null) {
            return;
        }

        try {
            String message = gson.toJson(Map.of(
                    "uid", uid,
                    "username", username,
                    "lastServer", lastServer,
                    "timestamp", System.currentTimeMillis(),
                    "type", "REAL_QUIT"
            ));

            long receivers = redisCommands.publish("yrdatabase:player:quit", message);
            getLogger().debug("已发布REAL_QUIT消息，接收者数量: " + receivers);

        } catch (Exception e) {
            getLogger().error("发布REAL_QUIT消息失败", e);
        }
    }

    private long getPlayerUid(ProxiedPlayer player) {
        UUID uuid = player.getUniqueId();
        return uuid != null ? (uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits()) : player.getName().hashCode();
    }
}
