package com.yirankuma.yrdatabase.api.session;

import com.yirankuma.yrdatabase.api.event.SessionReason;

/**
 * Data class for session events received from Redis Pub/Sub.
 * This is a platform-independent representation of session events.
 *
 * @author YiranKuma
 */
public class SessionEventData {

    private final String playerId;
    private final String playerName;
    private final SessionReason reason;
    private final long timestamp;
    private final String fromServer;
    private final String toServer;

    private SessionEventData(Builder builder) {
        this.playerId = builder.playerId;
        this.playerName = builder.playerName;
        this.reason = builder.reason;
        this.timestamp = builder.timestamp;
        this.fromServer = builder.fromServer;
        this.toServer = builder.toServer;
    }

    /**
     * Get the player's unique identifier (UUID).
     */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * Get the player's display name.
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Get the session reason (REAL_JOIN, REAL_QUIT, SERVER_TRANSFER, etc.).
     */
    public SessionReason getReason() {
        return reason;
    }

    /**
     * Get the timestamp when the event occurred.
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Get the source server name (for transfer events).
     */
    public String getFromServer() {
        return fromServer;
    }

    /**
     * Get the target server name (for transfer events).
     */
    public String getToServer() {
        return toServer;
    }

    /**
     * Check if this event indicates the player truly left the network.
     * Data should be persisted in this case.
     */
    public boolean shouldPersist() {
        return reason == SessionReason.REAL_QUIT 
            || reason == SessionReason.LOCAL_QUIT
            || reason == SessionReason.SERVER_SHUTDOWN;
    }

    /**
     * Check if this is a server transfer (data should stay in cache).
     */
    public boolean isTransfer() {
        return reason == SessionReason.SERVER_TRANSFER;
    }

    @Override
    public String toString() {
        return "SessionEventData{" +
                "playerId='" + playerId + '\'' +
                ", playerName='" + playerName + '\'' +
                ", reason=" + reason +
                ", timestamp=" + timestamp +
                ", fromServer='" + fromServer + '\'' +
                ", toServer='" + toServer + '\'' +
                '}';
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String playerId;
        private String playerName;
        private SessionReason reason;
        private long timestamp = System.currentTimeMillis();
        private String fromServer;
        private String toServer;

        public Builder playerId(String playerId) {
            this.playerId = playerId;
            return this;
        }

        public Builder playerName(String playerName) {
            this.playerName = playerName;
            return this;
        }

        public Builder reason(SessionReason reason) {
            this.reason = reason;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder fromServer(String fromServer) {
            this.fromServer = fromServer;
            return this;
        }

        public Builder toServer(String toServer) {
            this.toServer = toServer;
            return this;
        }

        public SessionEventData build() {
            if (playerId == null || playerName == null || reason == null) {
                throw new IllegalStateException("playerId, playerName, and reason are required");
            }
            return new SessionEventData(this);
        }
    }
}
