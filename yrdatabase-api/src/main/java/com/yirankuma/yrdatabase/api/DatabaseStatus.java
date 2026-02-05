package com.yirankuma.yrdatabase.api;

import lombok.Builder;
import lombok.Data;

/**
 * Database connection status information.
 *
 * @author YiranKuma
 */
@Data
@Builder
public class DatabaseStatus {

    /**
     * Overall connected status.
     */
    private final boolean connected;

    /**
     * Cache layer (Redis) status.
     */
    private final ProviderStatus cacheStatus;

    /**
     * Persistence layer (MySQL/SQLite) status.
     */
    private final ProviderStatus persistStatus;

    /**
     * Number of cached entries.
     */
    private final long cachedEntries;

    /**
     * Number of pending persistence operations.
     */
    private final long pendingPersist;

    @Data
    @Builder
    public static class ProviderStatus {
        private final boolean enabled;
        private final boolean connected;
        private final String type;
        private final String host;
        private final int port;
        private final long latencyMs;
        private final String errorMessage;
    }
}
