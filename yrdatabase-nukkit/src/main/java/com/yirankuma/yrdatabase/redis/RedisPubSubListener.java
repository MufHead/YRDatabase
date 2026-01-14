package com.yirankuma.yrdatabase.redis;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yirankuma.yrdatabase.YRDatabase;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis Pub/Sub监听器
 *
 * 监听WaterdogPE发送的玩家真实加入/退出消息
 * 用于区分转服和真实加入/退出，避免转服时触发持久化
 */
public class RedisPubSubListener implements io.lettuce.core.pubsub.RedisPubSubListener<String, String> {

    private final YRDatabase plugin;
    private final Gson gson;
    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    // 记录真实在线的玩家（UID -> 加入时间戳）
    private final Map<Long, Long> realOnlinePlayers = new ConcurrentHashMap<>();

    public RedisPubSubListener(YRDatabase plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * 初始化Redis Pub/Sub连接
     */
    public void initialize(String host, int port, String password, int database, int timeout) {
        try {
            // 构建Redis URI
            RedisURI.Builder uriBuilder = RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .withDatabase(database)
                    .withTimeout(Duration.ofMillis(timeout));

            if (password != null && !password.isEmpty()) {
                uriBuilder.withPassword(password.toCharArray());
            }

            redisClient = RedisClient.create(uriBuilder.build());
            pubSubConnection = redisClient.connectPubSub();

            // 添加监听器
            pubSubConnection.addListener(this);

            // 订阅频道
            RedisPubSubAsyncCommands<String, String> async = pubSubConnection.async();
            async.subscribe("yrdatabase:player:join");
            async.subscribe("yrdatabase:player:quit");
            async.subscribe("yrdatabase:heartbeat");

            plugin.getLogger().info("Redis Pub/Sub 订阅成功");
            plugin.getLogger().info("监听频道: yrdatabase:player:join, yrdatabase:player:quit, yrdatabase:heartbeat");

        } catch (Exception e) {
            plugin.getLogger().error("Redis Pub/Sub 初始化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭连接
     */
    public void shutdown() {
        if (pubSubConnection != null) {
            pubSubConnection.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        realOnlinePlayers.clear();
    }

    /**
     * 检查玩家是否真实在线（非转服）
     */
    public boolean isRealOnline(long uid) {
        return realOnlinePlayers.containsKey(uid);
    }

    /**
     * 移除玩家会话
     */
    public void removePlayerSession(long uid) {
        realOnlinePlayers.remove(uid);
    }

    /**
     * 获取当前真实在线玩家数
     */
    public int getRealOnlineCount() {
        return realOnlinePlayers.size();
    }

    // ========== RedisPubSubListener 接口实现 ==========

    @Override
    public void message(String channel, String message) {
        try {
            if (channel.equals("yrdatabase:player:join")) {
                handleRealJoin(message);
            } else if (channel.equals("yrdatabase:player:quit")) {
                handleRealQuit(message);
            } else if (channel.equals("yrdatabase:heartbeat")) {
                handleHeartbeat(message);
            }
        } catch (Exception e) {
            plugin.getLogger().error("处理Redis消息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void message(String pattern, String channel, String message) {
        // 模式订阅消息（当前未使用）
    }

    @Override
    public void subscribed(String channel, long count) {
        plugin.getLogger().info("订阅成功: " + channel + " (订阅数: " + count + ")");
    }

    @Override
    public void psubscribed(String pattern, long count) {
        // 模式订阅成功（当前未使用）
    }

    @Override
    public void unsubscribed(String channel, long count) {
        plugin.getLogger().info("取消订阅: " + channel + " (剩余订阅数: " + count + ")");
    }

    @Override
    public void punsubscribed(String pattern, long count) {
        // 取消模式订阅（当前未使用）
    }

    // ========== 消息处理方法 ==========

    /**
     * 处理玩家真实加入消息
     */
    private void handleRealJoin(String message) {
        try {
            JsonObject data = JsonParser.parseString(message).getAsJsonObject();
            long uid = data.get("uid").getAsLong();
            String username = data.get("username").getAsString();
            long timestamp = data.get("timestamp").getAsLong();

            // 记录玩家真实在线
            realOnlinePlayers.put(uid, timestamp);

            plugin.getLogger().info("收到REAL_JOIN: " + username + " (UID: " + uid + ")");
            plugin.getLogger().debug("当前真实在线玩家数: " + realOnlinePlayers.size());

            // 触发事件管理器
            if (plugin.getEventManager() != null) {
                plugin.getEventManager().onRealJoinFromWaterdog(String.valueOf(uid), username);
            }

        } catch (Exception e) {
            plugin.getLogger().error("解析REAL_JOIN消息失败: " + message, e);
        }
    }

    /**
     * 处理玩家真实退出消息
     */
    private void handleRealQuit(String message) {
        try {
            JsonObject data = JsonParser.parseString(message).getAsJsonObject();
            long uid = data.get("uid").getAsLong();
            String username = data.get("username").getAsString();
            String lastServer = data.get("lastServer").getAsString();

            // 移除玩家会话
            realOnlinePlayers.remove(uid);

            plugin.getLogger().info("收到REAL_QUIT: " + username + " (UID: " + uid + ", 最后所在: " + lastServer + ")");
            plugin.getLogger().debug("当前真实在线玩家数: " + realOnlinePlayers.size());

            // 触发事件管理器
            if (plugin.getEventManager() != null) {
                plugin.getEventManager().onRealQuitFromWaterdog(String.valueOf(uid), username);
            }

        } catch (Exception e) {
            plugin.getLogger().error("解析REAL_QUIT消息失败: " + message, e);
        }
    }

    /**
     * 处理心跳消息（可选）
     */
    private void handleHeartbeat(String message) {
        try {
            JsonObject data = JsonParser.parseString(message).getAsJsonObject();
            long timestamp = data.get("timestamp").getAsLong();
            int online = data.get("online").getAsInt();
            int sessions = data.get("sessions").getAsInt();

            plugin.getLogger().debug("收到心跳: 在线=" + online + ", 会话=" + sessions);

        } catch (Exception e) {
            plugin.getLogger().error("解析心跳消息失败: " + message, e);
        }
    }
}
