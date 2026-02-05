package com.yirankuma.yrdatabase.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Type-safe repository for entity CRUD operations.
 *
 * @param <T> Entity type
 * @author YiranKuma
 */
public interface Repository<T> {

    /**
     * Find entity by primary key.
     *
     * @param id Primary key value
     * @return Entity wrapped in Optional
     */
    CompletableFuture<Optional<T>> findById(String id);

    /**
     * Find all entities in the table.
     *
     * @return List of all entities
     */
    CompletableFuture<List<T>> findAll();

    /**
     * Find entities by field value.
     *
     * @param field Field name
     * @param value Field value
     * @return List of matching entities
     */
    CompletableFuture<List<T>> findBy(String field, Object value);

    /**
     * Find entities by multiple conditions.
     *
     * @param conditions Field-value pairs
     * @return List of matching entities
     */
    CompletableFuture<List<T>> findByConditions(java.util.Map<String, Object> conditions);

    /**
     * Save entity using default cache strategy (CACHE_FIRST).
     *
     * @param entity Entity to save
     * @return Success status
     */
    CompletableFuture<Boolean> save(T entity);

    /**
     * Save entity with specific cache strategy.
     *
     * @param entity   Entity to save
     * @param strategy Cache strategy
     * @return Success status
     */
    CompletableFuture<Boolean> save(T entity, CacheStrategy strategy);

    /**
     * Save multiple entities.
     *
     * @param entities Entities to save
     * @return Success status
     */
    CompletableFuture<Boolean> saveAll(Collection<T> entities);

    /**
     * Save multiple entities with specific cache strategy.
     *
     * @param entities Entities to save
     * @param strategy Cache strategy
     * @return Success status
     */
    CompletableFuture<Boolean> saveAll(Collection<T> entities, CacheStrategy strategy);

    /**
     * Delete entity by primary key.
     *
     * @param id Primary key value
     * @return Success status
     */
    CompletableFuture<Boolean> deleteById(String id);

    /**
     * Delete all entities from the table.
     *
     * @return Success status
     */
    CompletableFuture<Boolean> deleteAll();

    /**
     * Check if entity exists by primary key.
     *
     * @param id Primary key value
     * @return Existence status
     */
    CompletableFuture<Boolean> existsById(String id);

    /**
     * Count all entities in the table.
     *
     * @return Number of entities
     */
    CompletableFuture<Long> count();

    /**
     * Persist entity from cache to database and clear cache.
     *
     * @param id Primary key value
     * @return Success status
     */
    CompletableFuture<Boolean> persistAndClear(String id);

    /**
     * Get the entity class this repository manages.
     *
     * @return Entity class
     */
    Class<T> getEntityClass();

    /**
     * Get the table name for this repository.
     *
     * @return Table name
     */
    String getTableName();
}
