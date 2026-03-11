package com.yirankuma.yrdatabase.api.provider;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Cache provider interface with additional cache-specific operations.
 * Typically implemented by Redis.
 *
 * @author YiranKuma
 */
public interface CacheProvider extends StorageProvider {

    /**
     * Set expiration time for key.
     *
     * @param key Key
     * @param ttl Time to live
     * @return Success status
     */
    CompletableFuture<Boolean> expire(String key, Duration ttl);

    /**
     * Get remaining TTL for key.
     *
     * @param key Key
     * @return TTL in seconds, -1 if no expiry, -2 if key doesn't exist
     */
    CompletableFuture<Long> ttl(String key);

    /**
     * Get keys matching pattern.
     *
     * @param pattern Pattern (e.g., "table:player:*")
     * @return List of matching keys
     */
    CompletableFuture<java.util.List<String>> keys(String pattern);

    // ==================== Pub/Sub Support ====================

    /**
     * Subscribe to a channel.
     *
     * @param channel Channel name
     * @param handler Message handler
     */
    void subscribe(String channel, Consumer<String> handler);

    /**
     * Unsubscribe from a channel.
     *
     * @param channel Channel name
     */
    void unsubscribe(String channel);

    /**
     * Publish message to a channel.
     *
     * @param channel Channel name
     * @param message Message content
     * @return Number of subscribers that received the message
     */
    CompletableFuture<Long> publish(String channel, String message);

    // ==================== Atomic Operations ====================

    /**
     * Increment value atomically.
     *
     * @param key Key
     * @return New value
     */
    CompletableFuture<Long> incr(String key);

    /**
     * Increment value by amount atomically.
     *
     * @param key    Key
     * @param amount Amount to increment
     * @return New value
     */
    CompletableFuture<Long> incrBy(String key, long amount);

    /**
     * Decrement value atomically.
     *
     * @param key Key
     * @return New value
     */
    CompletableFuture<Long> decr(String key);

    /**
     * Set value only if key doesn't exist.
     *
     * @param key   Key
     * @param value Value
     * @return True if set, false if key already exists
     */
    CompletableFuture<Boolean> setNx(String key, String value);

    /**
     * Set value only if key doesn't exist, with expiration.
     *
     * @param key   Key
     * @param value Value
     * @param ttl   Time to live
     * @return True if set, false if key already exists
     */
    CompletableFuture<Boolean> setNxEx(String key, String value, Duration ttl);

    // ==================== Sorted Set Operations ====================

    /**
     * Add member to sorted set with score.
     *
     * @param key    Sorted set key
     * @param score  Score (typically a timestamp)
     * @param member Member value
     * @return True if member was newly added
     */
    CompletableFuture<Boolean> zadd(String key, double score, String member);

    /**
     * Remove members from sorted set.
     *
     * @param key     Sorted set key
     * @param members Members to remove
     * @return Number of members removed
     */
    CompletableFuture<Long> zrem(String key, String... members);

    /**
     * Get members with score between min and max (inclusive).
     *
     * @param key Sorted set key
     * @param min Minimum score
     * @param max Maximum score
     * @return List of matching members
     */
    CompletableFuture<java.util.List<String>> zrangeByScore(String key, double min, double max);
}
