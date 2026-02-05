package com.yirankuma.yrdatabase.api;

/**
 * Cache strategy for data persistence.
 *
 * @author YiranKuma
 */
public enum CacheStrategy {

    /**
     * Write only to cache, no persistence.
     * Fast but data may be lost on restart.
     */
    CACHE_ONLY,

    /**
     * Write only to persistence layer, bypass cache.
     * Slower but ensures durability.
     */
    PERSIST_ONLY,

    /**
     * Write to cache first, persist later (on player quit/server shutdown).
     * Best performance for player data.
     */
    CACHE_FIRST,

    /**
     * Write to both cache and persistence layer synchronously.
     * Slower but ensures consistency.
     */
    WRITE_THROUGH
}
