package com.yirankuma.yrdatabase.api.event;

/**
 * Event fired when player data should be initialized/loaded.
 *
 * @author YiranKuma
 */
public interface PlayerDataInitEvent extends PlayerDataEvent {

    /**
     * Check if data should be loaded from database.
     * Returns false for server transfers (data already in memory/cache).
     *
     * @return True if data should be loaded
     */
    default boolean shouldLoadData() {
        SessionReason reason = getReason();
        return reason == SessionReason.REAL_JOIN || reason == SessionReason.LOCAL_JOIN;
    }

    /**
     * Check if this is a real join (not a server transfer).
     *
     * @return True if real join
     */
    default boolean isRealJoin() {
        return getReason() == SessionReason.REAL_JOIN;
    }

    /**
     * Check if this is a local join (standalone mode).
     *
     * @return True if local join
     */
    default boolean isLocalJoin() {
        return getReason() == SessionReason.LOCAL_JOIN;
    }

    /**
     * Check if this is a server transfer.
     *
     * @return True if server transfer
     */
    default boolean isServerTransfer() {
        return getReason() == SessionReason.SERVER_TRANSFER;
    }
}
