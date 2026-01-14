package com.yirankuma.yrdatabase.common.protocol;

/**
 * 协议常量定义
 */
public final class ProtocolConstants {
    private ProtocolConstants() {}

    // ============ Plugin Channel ============

    /**
     * 主通信频道名称
     */
    public static final String MAIN_CHANNEL = "yrdatabase:main";

    /**
     * 心跳频道（可选，用于健康检查）
     */
    public static final String HEARTBEAT_CHANNEL = "yrdatabase:heartbeat";

    // ============ 数据键常量 ============

    /**
     * 玩家UID键
     */
    public static final String KEY_UID = "uid";

    /**
     * 玩家用户名键
     */
    public static final String KEY_USERNAME = "username";

    /**
     * 服务器名称键
     */
    public static final String KEY_SERVER = "server";

    /**
     * 来源服务器键
     */
    public static final String KEY_FROM_SERVER = "fromServer";

    /**
     * 目标服务器键
     */
    public static final String KEY_TO_SERVER = "toServer";

    /**
     * 时间戳键
     */
    public static final String KEY_TIMESTAMP = "timestamp";

    /**
     * 请求ID键
     */
    public static final String KEY_REQUEST_ID = "requestId";

    /**
     * 在线状态键
     */
    public static final String KEY_IS_ONLINE = "isOnline";

    /**
     * 玩家数量键
     */
    public static final String KEY_PLAYER_COUNT = "playerCount";

    // ============ 超时配置 ============

    /**
     * 玩家会话超时时间（毫秒）
     * 如果在此时间内未收到REAL_QUIT消息，视为异常断线
     */
    public static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5分钟

    /**
     * 消息有效期（毫秒）
     * 超过此时间的消息将被丢弃
     */
    public static final long MESSAGE_EXPIRY_MS = 30 * 1000; // 30秒

    /**
     * 心跳间隔（毫秒）
     */
    public static final long HEARTBEAT_INTERVAL_MS = 10 * 1000; // 10秒

    /**
     * 同步请求超时（毫秒）
     */
    public static final long SYNC_TIMEOUT_MS = 5 * 1000; // 5秒

    // ============ Redis键前缀 ============

    /**
     * 玩家在线状态键前缀
     * 格式: yrdatabase:online:{uid}
     */
    public static final String REDIS_KEY_ONLINE_PREFIX = "yrdatabase:online:";

    /**
     * 玩家会话锁键前缀
     * 格式: yrdatabase:lock:{uid}
     */
    public static final String REDIS_KEY_LOCK_PREFIX = "yrdatabase:lock:";

    /**
     * 分布式锁过期时间（秒）
     */
    public static final int LOCK_EXPIRE_SECONDS = 30;
}
