package com.yirankuma.yrdatabase.api;

import com.yirankuma.yrdatabase.api.provider.CacheProvider;
import com.yirankuma.yrdatabase.api.provider.PersistProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Main database manager interface.
 * Provides unified access to cache and persistence layers.
 *
 * @author YiranKuma
 */
public interface DatabaseManager extends AutoCloseable {

    // ==================== Simple Map API ====================

    /**
     * Smart get: tries cache first, then persistence layer.
     *
     * @param table Table name
     * @param key   Primary key
     * @return Data map wrapped in Optional
     */
    CompletableFuture<Optional<Map<String, Object>>> get(String table, String key);

    /**
     * Smart set: writes to cache with optional delayed persistence.
     *
     * @param table Table name
     * @param key   Primary key
     * @param data  Data to store
     * @return Success status
     */
    CompletableFuture<Boolean> set(String table, String key, Map<String, Object> data);

    /**
     * Smart set with cache strategy.
     *
     * @param table    Table name
     * @param key      Primary key
     * @param data     Data to store
     * @param strategy Cache strategy
     * @return Success status
     */
    CompletableFuture<Boolean> set(String table, String key, Map<String, Object> data, CacheStrategy strategy);

    /**
     * Persist data from cache to persistence layer and clear cache.
     *
     * @param table Table name
     * @param key   Primary key
     * @return Success status
     */
    CompletableFuture<Boolean> persistAndClear(String table, String key);

    /**
     * Delete data from both cache and persistence layer.
     *
     * @param table Table name
     * @param key   Primary key
     * @return Success status
     */
    CompletableFuture<Boolean> delete(String table, String key);

    /**
     * Check if data exists (in cache or persistence layer).
     *
     * @param table Table name
     * @param key   Primary key
     * @return Existence status
     */
    CompletableFuture<Boolean> exists(String table, String key);

    /**
     * Ensure table exists with the given schema.
     *
     * @param table  Table name
     * @param schema Column definitions (name -> SQL type)
     * @return Success status
     */
    CompletableFuture<Boolean> ensureTable(String table, Map<String, String> schema);

    // ==================== Type-safe Repository API ====================

    /**
     * Get a type-safe repository for entity operations.
     *
     * @param entityClass Entity class annotated with @Table
     * @param <T>         Entity type
     * @return Repository instance
     */
    <T> Repository<T> getRepository(Class<T> entityClass);

    // ==================== Direct Provider Access ====================

    /**
     * Get the cache provider (Redis).
     *
     * @return Cache provider if available
     */
    Optional<CacheProvider> getCacheProvider();

    /**
     * Get the persistence provider (MySQL/SQLite).
     *
     * @return Persistence provider if available
     */
    Optional<PersistProvider> getPersistProvider();

    // ==================== Status ====================

    /**
     * Check if any storage backend is connected.
     *
     * @return Connection status
     */
    boolean isConnected();

    /**
     * Get detailed database status.
     *
     * @return Status information
     */
    DatabaseStatus getStatus();

    /**
     * Shutdown and cleanup resources.
     */
    @Override
    void close();
}
