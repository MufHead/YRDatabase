package com.yirankuma.yrdatabase.api.provider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for all storage providers.
 *
 * @author YiranKuma
 */
public interface StorageProvider extends AutoCloseable {

    // ==================== Basic Key-Value Operations ====================

    /**
     * Get value by key.
     *
     * @param key Key
     * @return Value wrapped in Optional
     */
    CompletableFuture<Optional<String>> get(String key);

    /**
     * Set value for key.
     *
     * @param key   Key
     * @param value Value
     * @return Success status
     */
    CompletableFuture<Boolean> set(String key, String value);

    /**
     * Set value with expiration.
     *
     * @param key   Key
     * @param value Value
     * @param ttl   Time to live
     * @return Success status
     */
    CompletableFuture<Boolean> setEx(String key, String value, Duration ttl);

    /**
     * Delete key.
     *
     * @param key Key
     * @return Success status
     */
    CompletableFuture<Boolean> delete(String key);

    /**
     * Check if key exists.
     *
     * @param key Key
     * @return Existence status
     */
    CompletableFuture<Boolean> exists(String key);

    // ==================== Batch Operations ====================

    /**
     * Get multiple values.
     *
     * @param keys Keys
     * @return Map of key to value
     */
    CompletableFuture<Map<String, String>> mget(List<String> keys);

    /**
     * Set multiple values.
     *
     * @param entries Key-value pairs
     * @return Success status
     */
    CompletableFuture<Boolean> mset(Map<String, String> entries);

    // ==================== Hash Operations ====================

    /**
     * Get hash field value.
     *
     * @param key   Hash key
     * @param field Field name
     * @return Field value wrapped in Optional
     */
    CompletableFuture<Optional<String>> hget(String key, String field);

    /**
     * Set hash field value.
     *
     * @param key   Hash key
     * @param field Field name
     * @param value Field value
     * @return Success status
     */
    CompletableFuture<Boolean> hset(String key, String field, String value);

    /**
     * Get all hash fields and values.
     *
     * @param key Hash key
     * @return Map of field to value
     */
    CompletableFuture<Map<String, String>> hgetAll(String key);

    /**
     * Delete hash fields.
     *
     * @param key    Hash key
     * @param fields Fields to delete
     * @return Success status
     */
    CompletableFuture<Boolean> hdel(String key, String... fields);

    /**
     * Set multiple hash fields.
     *
     * @param key    Hash key
     * @param fields Field-value pairs
     * @return Success status
     */
    CompletableFuture<Boolean> hmset(String key, Map<String, String> fields);

    // ==================== Status ====================

    /**
     * Check if provider is connected.
     *
     * @return Connection status
     */
    boolean isConnected();

    /**
     * Get provider type.
     *
     * @return Provider type
     */
    ProviderType getType();

    /**
     * Reconnect to the backend.
     *
     * @return Completion future
     */
    CompletableFuture<Void> reconnect();

    /**
     * Ping the backend to check connectivity.
     *
     * @return Latency in milliseconds, or -1 if failed
     */
    CompletableFuture<Long> ping();

    /**
     * Close the provider and release resources.
     */
    @Override
    void close();
}
