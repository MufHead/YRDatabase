package com.yirankuma.yrdatabase.api.event;

/**
 * Reason for player session events.
 *
 * @author YiranKuma
 */
public enum SessionReason {

    /**
     * Player truly joined the proxy (confirmed by WaterdogPE).
     */
    REAL_JOIN,

    /**
     * Player truly quit the proxy (confirmed by WaterdogPE).
     */
    REAL_QUIT,

    /**
     * Player joined locally (standalone mode, no proxy).
     */
    LOCAL_JOIN,

    /**
     * Player quit locally (standalone mode, no proxy).
     */
    LOCAL_QUIT,

    /**
     * Player transferred between servers (should not persist data).
     */
    SERVER_TRANSFER,

    /**
     * Server is shutting down (force persist all data).
     */
    SERVER_SHUTDOWN
}
