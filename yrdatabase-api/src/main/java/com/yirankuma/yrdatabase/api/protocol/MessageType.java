package com.yirankuma.yrdatabase.api.protocol;

/**
 * Message types for cross-server communication.
 *
 * @author YiranKuma
 */
public enum MessageType {
    /**
     * Player joined the proxy network.
     */
    PLAYER_JOIN,

    /**
     * Player quit the proxy network.
     */
    PLAYER_QUIT,

    /**
     * Player transferred to another server.
     */
    PLAYER_TRANSFER,

    /**
     * Heartbeat message to keep session alive.
     */
    HEARTBEAT,

    /**
     * Request to sync player data.
     */
    DATA_SYNC_REQUEST,

    /**
     * Response to data sync request.
     */
    DATA_SYNC_RESPONSE
}
