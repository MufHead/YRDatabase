package com.yirankuma.yrdatabase.api.event;

/**
 * Event fired when player data should be saved/persisted.
 *
 * @author YiranKuma
 */
public interface PlayerDataSaveEvent extends PlayerDataEvent {

    /**
     * Check if data should be persisted to database.
     * Returns false for server transfers (data stays in cache).
     *
     * @return True if data should be persisted
     */
    default boolean shouldPersist() {
        SessionReason reason = getReason();
        return reason != SessionReason.SERVER_TRANSFER;
    }

    /**
     * Check if this is a real quit (confirmed by proxy).
     *
     * @return True if real quit
     */
    default boolean isRealQuit() {
        return getReason() == SessionReason.REAL_QUIT;
    }

    /**
     * Check if this is a local quit (standalone mode).
     *
     * @return True if local quit
     */
    default boolean isLocalQuit() {
        return getReason() == SessionReason.LOCAL_QUIT;
    }

    /**
     * Check if this is a server shutdown.
     *
     * @return True if server shutdown
     */
    default boolean isServerShutdown() {
        return getReason() == SessionReason.SERVER_SHUTDOWN;
    }

    /**
     * Check if this is a server transfer (should not persist).
     *
     * @return True if server transfer
     */
    default boolean isServerTransfer() {
        return getReason() == SessionReason.SERVER_TRANSFER;
    }

    /**
     * Cancel the persistence operation.
     * Only effective if shouldPersist() is true.
     */
    void cancel();

    /**
     * Check if the event has been cancelled.
     *
     * @return True if cancelled
     */
    boolean isCancelled();
}
