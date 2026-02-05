package com.yirankuma.yrdatabase.core;

import com.google.gson.Gson;
import com.yirankuma.yrdatabase.api.CacheStrategy;
import com.yirankuma.yrdatabase.api.Repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of type-safe Repository.
 *
 * @param <T> Entity type
 * @author YiranKuma
 */
public class RepositoryImpl<T> implements Repository<T> {

    private final DatabaseManagerImpl databaseManager;
    private final Class<T> entityClass;
    private final EntityMapper<T> entityMapper;
    private final Gson gson;
    private volatile boolean tableEnsured = false;

    public RepositoryImpl(DatabaseManagerImpl databaseManager, Class<T> entityClass, Gson gson) {
        this.databaseManager = databaseManager;
        this.entityClass = entityClass;
        this.entityMapper = new EntityMapper<>(entityClass);
        this.gson = gson;
    }

    private CompletableFuture<Void> ensureTable() {
        if (tableEnsured) {
            return CompletableFuture.completedFuture(null);
        }

        return databaseManager.ensureTable(entityMapper.getTableName(), entityMapper.getTableSchema())
                .thenAccept(success -> {
                    if (success) {
                        tableEnsured = true;
                    }
                });
    }

    @Override
    public CompletableFuture<Optional<T>> findById(String id) {
        return ensureTable().thenCompose(v ->
                databaseManager.get(entityMapper.getTableName(), id)
                        .thenApply(opt -> opt.map(entityMapper::fromMap))
        );
    }

    @Override
    public CompletableFuture<List<T>> findAll() {
        return ensureTable().thenCompose(v -> {
            var persistProvider = databaseManager.getPersistProvider();
            if (persistProvider.isEmpty() || !persistProvider.get().isConnected()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            return persistProvider.get().queryAll(entityMapper.getTableName())
                    .thenApply(rows -> rows.stream()
                            .map(entityMapper::fromMap)
                            .collect(Collectors.toList()));
        });
    }

    @Override
    public CompletableFuture<List<T>> findBy(String field, Object value) {
        return findByConditions(Map.of(field, value));
    }

    @Override
    public CompletableFuture<List<T>> findByConditions(Map<String, Object> conditions) {
        return ensureTable().thenCompose(v -> {
            var persistProvider = databaseManager.getPersistProvider();
            if (persistProvider.isEmpty() || !persistProvider.get().isConnected()) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            return persistProvider.get().query(entityMapper.getTableName(), conditions)
                    .thenApply(rows -> rows.stream()
                            .map(entityMapper::fromMap)
                            .collect(Collectors.toList()));
        });
    }

    @Override
    public CompletableFuture<Boolean> save(T entity) {
        return save(entity, CacheStrategy.CACHE_FIRST);
    }

    @Override
    public CompletableFuture<Boolean> save(T entity, CacheStrategy strategy) {
        String id = entityMapper.getPrimaryKeyValue(entity);
        if (id == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Entity must have a primary key value"));
        }

        Map<String, Object> data = entityMapper.toMap(entity);
        return ensureTable().thenCompose(v ->
                databaseManager.set(entityMapper.getTableName(), id, data, strategy));
    }

    @Override
    public CompletableFuture<Boolean> saveAll(Collection<T> entities) {
        return saveAll(entities, CacheStrategy.CACHE_FIRST);
    }

    @Override
    public CompletableFuture<Boolean> saveAll(Collection<T> entities, CacheStrategy strategy) {
        if (entities.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        List<CompletableFuture<Boolean>> futures = entities.stream()
                .map(entity -> save(entity, strategy))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().allMatch(CompletableFuture::join));
    }

    @Override
    public CompletableFuture<Boolean> deleteById(String id) {
        return ensureTable().thenCompose(v ->
                databaseManager.delete(entityMapper.getTableName(), id));
    }

    @Override
    public CompletableFuture<Boolean> deleteAll() {
        return ensureTable().thenCompose(v -> {
            var persistProvider = databaseManager.getPersistProvider();
            if (persistProvider.isEmpty() || !persistProvider.get().isConnected()) {
                return CompletableFuture.completedFuture(false);
            }

            return persistProvider.get().deleteWhere(entityMapper.getTableName(), null)
                    .thenApply(count -> count >= 0);
        });
    }

    @Override
    public CompletableFuture<Boolean> existsById(String id) {
        return ensureTable().thenCompose(v ->
                databaseManager.exists(entityMapper.getTableName(), id));
    }

    @Override
    public CompletableFuture<Long> count() {
        return ensureTable().thenCompose(v -> {
            var persistProvider = databaseManager.getPersistProvider();
            if (persistProvider.isEmpty() || !persistProvider.get().isConnected()) {
                return CompletableFuture.completedFuture(0L);
            }

            return persistProvider.get().countAll(entityMapper.getTableName());
        });
    }

    @Override
    public CompletableFuture<Boolean> persistAndClear(String id) {
        return databaseManager.persistAndClear(entityMapper.getTableName(), id);
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public String getTableName() {
        return entityMapper.getTableName();
    }
}
