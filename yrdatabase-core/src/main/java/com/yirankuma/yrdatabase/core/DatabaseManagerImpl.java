package com.yirankuma.yrdatabase.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yirankuma.yrdatabase.api.CacheStrategy;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.api.DatabaseStatus;
import com.yirankuma.yrdatabase.api.Repository;
import com.yirankuma.yrdatabase.api.config.DatabaseConfig;
import com.yirankuma.yrdatabase.api.provider.CacheProvider;
import com.yirankuma.yrdatabase.api.provider.PersistProvider;
import com.yirankuma.yrdatabase.core.provider.mysql.MySQLProvider;
import com.yirankuma.yrdatabase.core.provider.redis.RedisProvider;
import com.yirankuma.yrdatabase.core.provider.sqlite.SQLiteProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core implementation of DatabaseManager.
 *
 * @author YiranKuma
 */
@Slf4j
public class DatabaseManagerImpl implements DatabaseManager {

    @Getter
    private final DatabaseConfig config;
    private final Gson gson;

    private RedisProvider redisProvider;
    private PersistProvider persistProvider;

    private final Map<Class<?>, Repository<?>> repositories = new ConcurrentHashMap<>();
    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    public DatabaseManagerImpl(DatabaseConfig config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    /**
     * Initialize all database connections.
     *
     * @return Completion future
     */
    public CompletableFuture<Void> initialize() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Initialize cache provider (Redis)
        if (config.getCache().isEnabled()) {
            redisProvider = new RedisProvider(config.getCache());
            futures.add(redisProvider.initialize().exceptionally(e -> {
                log.warn("Failed to initialize Redis: {}", e.getMessage());
                redisProvider = null;
                return null;
            }));
        }

        // Initialize persistence provider (MySQL or SQLite)
        if (config.getPersist().isEnabled()) {
            String type = config.getPersist().getType().toLowerCase();
            switch (type) {
                case "mysql":
                    MySQLProvider mysqlProvider = new MySQLProvider(config.getPersist().getMysql());
                    futures.add(mysqlProvider.initialize().thenRun(() -> {
                        persistProvider = mysqlProvider;
                    }).exceptionally(e -> {
                        log.warn("Failed to initialize MySQL: {}", e.getMessage());
                        return null;
                    }));
                    break;
                case "sqlite":
                default:
                    SQLiteProvider sqliteProvider = new SQLiteProvider(config.getPersist().getSqlite());
                    futures.add(sqliteProvider.initialize().thenRun(() -> {
                        persistProvider = sqliteProvider;
                    }).exceptionally(e -> {
                        log.warn("Failed to initialize SQLite: {}", e.getMessage());
                        return null;
                    }));
                    break;
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("YRDatabase initialized. Cache: {}, Persist: {}",
                        redisProvider != null && redisProvider.isConnected() ? "connected" : "disabled",
                        persistProvider != null && persistProvider.isConnected() ? "connected" : "disabled"));
    }

    // ==================== Simple Map API ====================

    @Override
    public CompletableFuture<Optional<Map<String, Object>>> get(String table, String key) {
        String cacheKey = buildCacheKey(table, key);

        // Try cache first
        if (redisProvider != null && redisProvider.isConnected()) {
            return redisProvider.get(cacheKey).thenCompose(cached -> {
                if (cached.isPresent()) {
                    // Cache hit
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = gson.fromJson(cached.get(), Map.class);
                    return CompletableFuture.completedFuture(Optional.ofNullable(data));
                }

                // Cache miss, try persistence
                return getFromPersist(table, key).thenCompose(persisted -> {
                    if (persisted.isPresent() && redisProvider != null) {
                        // Write back to cache
                        String json = gson.toJson(persisted.get());
                        long ttl = config.getCaching().getDefaultTTL();
                        redisProvider.setEx(cacheKey, json, Duration.ofSeconds(ttl));
                    }
                    return CompletableFuture.completedFuture(persisted);
                });
            });
        }

        // No cache, go directly to persistence
        return getFromPersist(table, key);
    }

    private CompletableFuture<Optional<Map<String, Object>>> getFromPersist(String table, String key) {
        if (persistProvider == null || !persistProvider.isConnected()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return persistProvider.query(table, Map.of("id", key))
                .<Optional<Map<String, Object>>>thenApply(results -> 
                    results.isEmpty() ? Optional.empty() : Optional.of(results.get(0)))
                .exceptionally(e -> {
                    log.debug("Query failed for {}/{}: {}", table, key, e.getMessage());
                    return Optional.empty();
                });
    }

    @Override
    public CompletableFuture<Boolean> set(String table, String key, Map<String, Object> data) {
        return set(table, key, data, CacheStrategy.CACHE_FIRST);
    }

    @Override
    public CompletableFuture<Boolean> set(String table, String key, Map<String, Object> data, CacheStrategy strategy) {
        String cacheKey = buildCacheKey(table, key);
        String json = gson.toJson(data);
        long ttl = config.getCaching().getDefaultTTL();

        // Ensure data has the key
        Map<String, Object> dataWithKey = new HashMap<>(data);
        dataWithKey.put("id", key);

        switch (strategy) {
            case CACHE_ONLY:
                if (redisProvider != null && redisProvider.isConnected()) {
                    return redisProvider.setEx(cacheKey, json, Duration.ofSeconds(ttl));
                }
                return CompletableFuture.completedFuture(false);

            case PERSIST_ONLY:
                return saveToPersist(table, key, dataWithKey);

            case WRITE_THROUGH:
                CompletableFuture<Boolean> persistFuture = saveToPersist(table, key, dataWithKey);
                if (redisProvider != null && redisProvider.isConnected()) {
                    return persistFuture.thenCompose(persistOk -> 
                        redisProvider.setEx(cacheKey, json, Duration.ofSeconds(ttl))
                            .thenApply(cacheOk -> persistOk && cacheOk)
                    );
                }
                return persistFuture;

            case CACHE_FIRST:
            default:
                if (redisProvider != null && redisProvider.isConnected()) {
                    return redisProvider.setEx(cacheKey, json, Duration.ofSeconds(ttl));
                }
                // Fallback to persist if no cache
                return saveToPersist(table, key, dataWithKey);
        }
    }

    private CompletableFuture<Boolean> saveToPersist(String table, String key, Map<String, Object> data) {
        if (persistProvider == null || !persistProvider.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        return persistProvider.upsert(table, data, "id");
    }

    @Override
    public CompletableFuture<Boolean> persistAndClear(String table, String key) {
        String cacheKey = buildCacheKey(table, key);

        if (redisProvider == null || !redisProvider.isConnected()) {
            return CompletableFuture.completedFuture(true);
        }

        return redisProvider.get(cacheKey).thenCompose(cached -> {
            if (cached.isEmpty()) {
                return CompletableFuture.completedFuture(true);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(cached.get(), Map.class);
            data.put("id", key);

            return saveToPersist(table, key, data).thenCompose(saved -> {
                if (saved) {
                    return redisProvider.delete(cacheKey);
                }
                return CompletableFuture.completedFuture(false);
            });
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(String table, String key) {
        String cacheKey = buildCacheKey(table, key);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        if (redisProvider != null && redisProvider.isConnected()) {
            futures.add(redisProvider.delete(cacheKey));
        }

        if (persistProvider != null && persistProvider.isConnected()) {
            futures.add(persistProvider.deleteWhere(table, Map.of("id", key))
                    .thenApply(count -> count > 0));
        }

        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().anyMatch(f -> f.join()));
    }

    @Override
    public CompletableFuture<Boolean> exists(String table, String key) {
        String cacheKey = buildCacheKey(table, key);

        // Check cache first
        if (redisProvider != null && redisProvider.isConnected()) {
            return redisProvider.exists(cacheKey).thenCompose(inCache -> {
                if (inCache) {
                    return CompletableFuture.completedFuture(true);
                }
                return existsInPersist(table, key);
            });
        }

        return existsInPersist(table, key);
    }

    private CompletableFuture<Boolean> existsInPersist(String table, String key) {
        if (persistProvider == null || !persistProvider.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        return persistProvider.count(table, Map.of("id", key))
                .thenApply(count -> count > 0)
                .exceptionally(e -> false);
    }

    @Override
    public CompletableFuture<Boolean> ensureTable(String table, Map<String, String> schema) {
        if (ensuredTables.contains(table)) {
            return CompletableFuture.completedFuture(true);
        }

        if (persistProvider == null || !persistProvider.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        return persistProvider.tableExists(table).thenCompose(exists -> {
            if (exists) {
                ensuredTables.add(table);
                return CompletableFuture.completedFuture(true);
            }

            return persistProvider.createTable(table, schema).thenApply(created -> {
                if (created) {
                    ensuredTables.add(table);
                }
                return created;
            });
        });
    }

    // ==================== Repository API ====================

    @Override
    @SuppressWarnings("unchecked")
    public <T> Repository<T> getRepository(Class<T> entityClass) {
        return (Repository<T>) repositories.computeIfAbsent(entityClass,
                cls -> new RepositoryImpl<>(this, entityClass, gson));
    }

    // ==================== Provider Access ====================

    @Override
    public Optional<CacheProvider> getCacheProvider() {
        return Optional.ofNullable(redisProvider);
    }

    @Override
    public Optional<PersistProvider> getPersistProvider() {
        return Optional.ofNullable(persistProvider);
    }

    // ==================== Status ====================

    @Override
    public boolean isConnected() {
        boolean cacheOk = redisProvider == null || redisProvider.isConnected();
        boolean persistOk = persistProvider == null || persistProvider.isConnected();
        return (redisProvider != null && redisProvider.isConnected()) ||
                (persistProvider != null && persistProvider.isConnected());
    }

    @Override
    public DatabaseStatus getStatus() {
        DatabaseStatus.ProviderStatus cacheStatus = null;
        DatabaseStatus.ProviderStatus persistStatus = null;

        if (redisProvider != null) {
            long latency = redisProvider.ping().join();
            cacheStatus = DatabaseStatus.ProviderStatus.builder()
                    .enabled(true)
                    .connected(redisProvider.isConnected())
                    .type("redis")
                    .host(config.getCache().getHost())
                    .port(config.getCache().getPort())
                    .latencyMs(latency)
                    .errorMessage(latency < 0 ? "Connection failed" : null)
                    .build();
        }

        if (persistProvider != null) {
            long latency = persistProvider.ping().join();
            String type = persistProvider.getType().name().toLowerCase();
            String host = type.equals("mysql") ? config.getPersist().getMysql().getHost() : "local";
            int port = type.equals("mysql") ? config.getPersist().getMysql().getPort() : 0;

            persistStatus = DatabaseStatus.ProviderStatus.builder()
                    .enabled(true)
                    .connected(persistProvider.isConnected())
                    .type(type)
                    .host(host)
                    .port(port)
                    .latencyMs(latency)
                    .errorMessage(latency < 0 ? "Connection failed" : null)
                    .build();
        }

        return DatabaseStatus.builder()
                .connected(isConnected())
                .cacheStatus(cacheStatus)
                .persistStatus(persistStatus)
                .cachedEntries(0) // Could be implemented with DBSIZE
                .pendingPersist(0)
                .build();
    }

    @Override
    public void close() {
        log.info("Shutting down YRDatabase...");

        if (redisProvider != null) {
            try {
                redisProvider.close();
            } catch (Exception e) {
                log.error("Error closing Redis: {}", e.getMessage());
            }
        }

        if (persistProvider != null) {
            try {
                persistProvider.close();
            } catch (Exception e) {
                log.error("Error closing persistence provider: {}", e.getMessage());
            }
        }

        repositories.clear();
        ensuredTables.clear();

        log.info("YRDatabase shutdown complete");
    }

    // ==================== Utilities ====================

    private String buildCacheKey(String table, String key) {
        return "yrdatabase:" + table + ":" + key;
    }

    /**
     * Get the Gson instance for serialization.
     *
     * @return Gson instance
     */
    public Gson getGson() {
        return gson;
    }
}
