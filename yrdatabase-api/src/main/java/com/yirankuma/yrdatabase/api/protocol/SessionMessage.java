package com.yirankuma.yrdatabase.api.protocol;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Session message for cross-server communication via plugin messages.
 *
 * @author YiranKuma
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionMessage {

    private static final Gson GSON = new Gson();

    /**
     * Message type.
     */
    private MessageType type;

    /**
     * Player UUID.
     */
    private String playerId;

    /**
     * Player name.
     */
    private String playerName;

    /**
     * Source server name.
     */
    private String sourceServer;

    /**
     * Target server name (for transfers).
     */
    private String targetServer;

    /**
     * Timestamp.
     */
    private long timestamp;

    /**
     * Additional data payload.
     */
    private Map<String, Object> data;

    /**
     * Create a player join message.
     */
    public static SessionMessage playerJoin(String playerId, String playerName, String serverName) {
        SessionMessage msg = new SessionMessage();
        msg.setType(MessageType.PLAYER_JOIN);
        msg.setPlayerId(playerId);
        msg.setPlayerName(playerName);
        msg.setSourceServer(serverName);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setData(new HashMap<>());
        return msg;
    }

    /**
     * Create a player quit message.
     */
    public static SessionMessage playerQuit(String playerId, String playerName, String serverName) {
        SessionMessage msg = new SessionMessage();
        msg.setType(MessageType.PLAYER_QUIT);
        msg.setPlayerId(playerId);
        msg.setPlayerName(playerName);
        msg.setSourceServer(serverName);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setData(new HashMap<>());
        return msg;
    }

    /**
     * Create a player transfer message.
     */
    public static SessionMessage playerTransfer(String playerId, String playerName, 
                                                 String fromServer, String toServer) {
        SessionMessage msg = new SessionMessage();
        msg.setType(MessageType.PLAYER_TRANSFER);
        msg.setPlayerId(playerId);
        msg.setPlayerName(playerName);
        msg.setSourceServer(fromServer);
        msg.setTargetServer(toServer);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setData(new HashMap<>());
        return msg;
    }

    /**
     * Create a heartbeat message.
     */
    public static SessionMessage heartbeat(String playerId, String serverName) {
        SessionMessage msg = new SessionMessage();
        msg.setType(MessageType.HEARTBEAT);
        msg.setPlayerId(playerId);
        msg.setSourceServer(serverName);
        msg.setTimestamp(System.currentTimeMillis());
        msg.setData(new HashMap<>());
        return msg;
    }

    /**
     * Serialize to JSON.
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Deserialize from JSON.
     */
    public static SessionMessage fromJson(String json) {
        return GSON.fromJson(json, SessionMessage.class);
    }

    /**
     * Serialize to bytes for plugin message.
     */
    public byte[] toBytes() {
        return toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Deserialize from bytes.
     */
    public static SessionMessage fromBytes(byte[] bytes) {
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return fromJson(json);
    }
}
