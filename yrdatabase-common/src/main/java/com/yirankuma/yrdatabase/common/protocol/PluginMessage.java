package com.yirankuma.yrdatabase.common.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件消息数据结构
 * 用于封装Plugin Messaging的消息内容
 */
public class PluginMessage {
    private final MessageType type;
    private final Map<String, Object> data;
    private final long timestamp;

    public PluginMessage(MessageType type) {
        this(type, new HashMap<>());
    }

    public PluginMessage(MessageType type, Map<String, Object> data) {
        this.type = type;
        this.data = data != null ? new HashMap<>(data) : new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    // ============ Getters ============

    public MessageType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }

    public long getTimestamp() {
        return timestamp;
    }

    // ============ 数据访问便捷方法 ============

    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    public Long getLong(String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public Integer getInt(String key) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public Boolean getBoolean(String key) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }

    // ============ Builder Pattern ============

    public static Builder builder(MessageType type) {
        return new Builder(type);
    }

    public static class Builder {
        private final MessageType type;
        private final Map<String, Object> data = new HashMap<>();

        public Builder(MessageType type) {
            this.type = type;
        }

        public Builder put(String key, Object value) {
            if (value != null) {
                data.put(key, value);
            }
            return this;
        }

        public Builder putUid(long uid) {
            return put("uid", uid);
        }

        public Builder putUsername(String username) {
            return put("username", username);
        }

        public Builder putServer(String serverName) {
            return put("server", serverName);
        }

        public Builder putFromServer(String serverName) {
            return put("fromServer", serverName);
        }

        public Builder putToServer(String serverName) {
            return put("toServer", serverName);
        }

        public PluginMessage build() {
            return new PluginMessage(type, data);
        }
    }

    @Override
    public String toString() {
        return "PluginMessage{" +
                "type=" + type +
                ", data=" + data +
                ", timestamp=" + timestamp +
                '}';
    }
}
