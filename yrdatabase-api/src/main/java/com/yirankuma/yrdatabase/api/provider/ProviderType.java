package com.yirankuma.yrdatabase.api.provider;

/**
 * Provider type enumeration.
 *
 * @author YiranKuma
 */
public enum ProviderType {
    /**
     * Redis cache provider.
     */
    REDIS,

    /**
     * MySQL persistence provider.
     */
    MYSQL,

    /**
     * SQLite persistence provider.
     */
    SQLITE,

    /**
     * PostgreSQL persistence provider.
     */
    POSTGRESQL,

    /**
     * In-memory provider (for testing).
     */
    MEMORY
}
