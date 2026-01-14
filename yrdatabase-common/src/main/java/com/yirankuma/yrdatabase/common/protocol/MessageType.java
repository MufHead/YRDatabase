package com.yirankuma.yrdatabase.common.protocol;

/**
 * Plugin Messaging 消息类型定义
 * 用于WaterdogPE和Nukkit之间的通信
 */
public enum MessageType {
    /**
     * 玩家真实加入代理（连接WaterdogPE）
     * 数据: {uid, timestamp, username}
     */
    REAL_JOIN((byte) 0x01),

    /**
     * 玩家真实退出代理（断开WaterdogPE连接）
     * 数据: {uid, timestamp, lastServer}
     */
    REAL_QUIT((byte) 0x02),

    /**
     * 玩家转服通知（可选，用于调试）
     * 数据: {uid, fromServer, toServer, timestamp}
     */
    SERVER_TRANSFER((byte) 0x03),

    /**
     * 心跳包（健康检查）
     * 数据: {serverName, timestamp, playerCount}
     */
    HEARTBEAT((byte) 0x04),

    /**
     * 数据同步请求（Nukkit请求玩家在线状态）
     * 数据: {uid, requestId}
     */
    SYNC_REQUEST((byte) 0x05),

    /**
     * 数据同步响应
     * 数据: {uid, requestId, isOnline, currentServer}
     */
    SYNC_RESPONSE((byte) 0x06);

    private final byte id;

    MessageType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    /**
     * 根据ID获取消息类型
     */
    public static MessageType fromId(byte id) {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type ID: " + id);
    }
}
