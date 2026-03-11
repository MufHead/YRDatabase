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
import java.util.concurrent.*;

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

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // pending 持久化集合 key，所有子服共享同一个 Redis sorted set
    private static final String PENDING_KEY = "yrdatabase:pending";
    // 分布式锁前缀（per-key 锁，用于 sweep）
    private static final String LOCK_PREFIX = "yrdatabase:lock:";
    // auto-sync 全局锁，保证同一时刻只有一台子服执行 sync
    private static final String SYNC_LOCK_KEY = "yrdatabase:sync_lock";
    // 扫描间隔（秒）
    private static final long SWEEP_INTERVAL_SECONDS = 30;
    // 提前量：TTL 剩余不足此值时触发持久化（必须 > SWEEP_INTERVAL_SECONDS）
    private static final long SWEEP_BUFFER_SECONDS = 60;
    // 分布式锁自动过期时间，防止持有锁的服务器崩溃后锁永不释放
    private static final long LOCK_TTL_SECONDS = 30;

    public DatabaseManagerImpl(DatabaseConfig config) {
        this.config = config;
        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();
    }

    /**
     * Initialize all database connections.
     *
     * @return Completion future with success status
     */
    @Override
    public CompletableFuture<Boolean> initialize() {
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
                .thenApply(v -> {
                    log.info("YRDatabase initialized. Cache: {}, Persist: {}",
                            redisProvider != null && redisProvider.isConnected() ? "connected" : "disabled",
                            persistProvider != null && persistProvider.isConnected() ? "connected" : "disabled");
                    startPendingSweep();
                    return isConnected();
                });
    }

    /**
     * 启动定期扫描任务，处理即将过期的 pending 持久化条目。
     * 仅在 Redis 和持久化层都可用时有意义。
     */
    private void startPendingSweep() {
        if (redisProvider == null || persistProvider == null) {
            return;
        }
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sweepPending(false);
            } catch (Exception e) {
                log.error("Pending sweep error: {}", e.getMessage());
            }
        }, SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Pending sweep started (interval={}s, buffer={}s)", SWEEP_INTERVAL_SECONDS, SWEEP_BUFFER_SECONDS);

        // auto-sync：定期将所有 pending 数据同步到持久层，子插件无需自行管理 flush 调度
        if (config.getCaching().isAutoSyncEnabled()) {
            int syncInterval = config.getCaching().getAutoSyncIntervalSeconds();
            if (syncInterval > 0) {
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        syncAllPending(syncInterval);
                    } catch (Exception e) {
                        log.error("Auto sync error: {}", e.getMessage());
                    }
                }, syncInterval, syncInterval, TimeUnit.SECONDS);
                log.info("Auto sync started (interval={}s)", syncInterval);
            }
        }
    }

    /**
     * 将 pending 集合中所有 key 同步到持久层（不删除 Redis 缓存）。
     * 使用 Redis 全局锁保证同一时刻只有一台子服执行，避免多服重复写库。
     * 锁 TTL = syncInterval，崩溃时自动释放。
     */
    private void syncAllPending(int syncInterval) {
        if (redisProvider == null || !redisProvider.isConnected()) return;
        if (persistProvider == null || !persistProvider.isConnected()) return;

        // 全局 sync 锁：TTL = syncInterval，确保每个周期内只有一台服执行
        redisProvider.setNxEx(SYNC_LOCK_KEY, "1", Duration.ofSeconds(syncInterval))
                .thenCompose(acquired -> {
                    if (!acquired) {
                        log.info("Auto sync skipped: another server holds the lock");
                        return CompletableFuture.completedFuture(null);
                    }
                    log.info("Auto sync lock acquired, scanning pending set...");
                    return redisProvider.zrangeByScore(PENDING_KEY, 0, Double.MAX_VALUE)
                            .thenAccept(members -> {
                                if (members.isEmpty()) {
                                    log.info("Auto sync: pending set is empty, nothing to persist");
                                    return;
                                }
                                log.info("Auto sync: persisting {} pending keys", members.size());
                                for (String cacheKey : members) {
                                    String[] parts = parseCacheKey(cacheKey);
                                    if (parts == null) {
                                        log.warn("Auto sync: could not parse cache key: {}", cacheKey);
                                        continue;
                                    }
                                    log.info("Auto sync: persisting {}/{}", parts[0], parts[1]);
                                    persistOnly(parts[0], parts[1]).thenAccept(ok -> {
                                        if (ok) {
                                            log.info("Auto sync: persisted {}/{} OK", parts[0], parts[1]);
                                        } else {
                                            log.warn("Auto sync: persist returned false for {}/{}", parts[0], parts[1]);
                                        }
                                    }).exceptionally(e -> {
                                        log.warn("Auto sync failed for {}: {}", cacheKey, e.getMessage());
                                        return null;
                                    });
                                }
                            });
                })
                .exceptionally(e -> {
                    log.error("Auto sync scan failed: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * 扫描 pending 集合，对即将过期（或 flushAll 时全部）的条目加锁并持久化。
     *
     * @param all true 时处理全部条目（关服 flush 用），false 时只处理快到期的
     */
    private void sweepPending(boolean all) {
        if (redisProvider == null || !redisProvider.isConnected()) return;
        if (persistProvider == null || !persistProvider.isConnected()) return;

        double maxScore = all
                ? Double.MAX_VALUE
                : (System.currentTimeMillis() / 1000.0) + SWEEP_BUFFER_SECONDS;

        redisProvider.zrangeByScore(PENDING_KEY, 0, maxScore).thenAccept(members -> {
            for (String cacheKey : members) {
                processPendingKey(cacheKey);
            }
        }).exceptionally(e -> {
            log.error("Failed to scan pending set: {}", e.getMessage());
            return null;
        });
    }

    /**
     * 对单个 pending 条目加分布式锁后持久化。
     */
    private void processPendingKey(String cacheKey) {
        String lockKey = LOCK_PREFIX + cacheKey;

        // 抢分布式锁，TTL 到期自动释放，防止崩溃后死锁
        redisProvider.setNxEx(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS))
                .thenCompose(acquired -> {
                    if (!acquired) {
                        // 其他子服已在处理
                        return CompletableFuture.completedFuture(false);
                    }
                    // 解析 cacheKey → table + key
                    String[] parts = parseCacheKey(cacheKey);
                    if (parts == null) {
                        redisProvider.zrem(PENDING_KEY, cacheKey);
                        redisProvider.delete(lockKey);
                        return CompletableFuture.completedFuture(false);
                    }
                    String table = parts[0];
                    String key = parts[1];

                    return redisProvider.get(cacheKey).thenCompose(cached -> {
                        CompletableFuture<Boolean> persistFuture;
                        if (cached.isPresent()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> latest = gson.fromJson(cached.get(), Map.class);
                            latest.put("id", key);
                            persistFuture = saveToPersist(table, key, latest);
                        } else {
                            // Redis 数据已过期，条目本身也可清除
                            log.debug("Pending key {} expired from Redis, removing from pending", cacheKey);
                            persistFuture = CompletableFuture.completedFuture(true);
                        }
                        return persistFuture.thenCompose(ok -> {
                            if (ok) {
                                redisProvider.zrem(PENDING_KEY, cacheKey);
                            }
                            // 无论成功与否都释放锁
                            return redisProvider.delete(lockKey).thenApply(d -> ok);
                        });
                    });
                })
                .exceptionally(e -> {
                    log.error("Error processing pending key {}: {}", cacheKey, e.getMessage());
                    redisProvider.delete(lockKey);
                    return false;
                });
    }

    /**
     * 解析 cacheKey（"yrdatabase:{table}:{key}"）为 [table, key]。
     */
    private String[] parseCacheKey(String cacheKey) {
        String prefix = "yrdatabase:";
        if (!cacheKey.startsWith(prefix)) return null;
        String rest = cacheKey.substring(prefix.length());
        int idx = rest.indexOf(':');
        if (idx < 1) return null;
        return new String[]{rest.substring(0, idx), rest.substring(idx + 1)};
    }

    /**
     * Flush all pending writes to persistence layer.
     * 扫描 pending 集合里的全部条目并持久化，用于关服时确保数据落库。
     */
    @Override
    public CompletableFuture<Void> flush() {
        if (redisProvider == null || !redisProvider.isConnected()
                || persistProvider == null || !persistProvider.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        return redisProvider.zrangeByScore(PENDING_KEY, 0, Double.MAX_VALUE).thenCompose(members -> {
            if (members.isEmpty()) return CompletableFuture.completedFuture(null);
            log.info("Flushing {} pending persist entries...", members.size());
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (String cacheKey : members) {
                futures.add(processPendingKeySync(cacheKey));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        }).exceptionally(e -> {
            log.error("Flush failed: {}", e.getMessage());
            return null;
        });
    }

    /**
     * flush 专用：同步等待每个 key 处理完成（不用分布式锁，关服时本服优先）。
     */
    private CompletableFuture<Void> processPendingKeySync(String cacheKey) {
        String[] parts = parseCacheKey(cacheKey);
        if (parts == null) {
            return redisProvider.zrem(PENDING_KEY, cacheKey).thenApply(r -> null);
        }
        String table = parts[0];
        String key = parts[1];

        return redisProvider.get(cacheKey).thenCompose(cached -> {
            if (cached.isPresent()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> latest = gson.fromJson(cached.get(), Map.class);
                latest.put("id", key);
                return saveToPersist(table, key, latest).thenCompose(ok -> {
                    if (ok) return redisProvider.zrem(PENDING_KEY, cacheKey).thenApply(r -> (Void) null);
                    return CompletableFuture.completedFuture((Void) null);
                });
            }
            return redisProvider.zrem(PENDING_KEY, cacheKey).thenApply(r -> (Void) null);
        }).exceptionally(e -> {
            log.error("Flush key {} failed: {}", cacheKey, e.getMessage());
            return (Void) null;
        });
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
                    // 写入 Redis，同时登记到 pending 集合（score = 过期时间戳，单位秒）
                    double expireAt = System.currentTimeMillis() / 1000.0 + ttl;
                    return redisProvider.setEx(cacheKey, json, Duration.ofSeconds(ttl))
                            .thenCompose(cacheOk -> {
                                if (cacheOk) {
                                    // 登记到 pending，供本服或其他子服的 sweep 处理
                                    redisProvider.zadd(PENDING_KEY, expireAt, cacheKey)
                                            .exceptionally(e -> {
                                                log.error("Failed to register pending for {}/{}: {}", table, key, e.getMessage());
                                                return false;
                                            });
                                }
                                return CompletableFuture.completedFuture(cacheOk);
                            });
                }
                return saveToPersist(table, key, dataWithKey);
        }
    }

    private CompletableFuture<Boolean> saveToPersist(String table, String key, Map<String, Object> data) {
        if (persistProvider == null || !persistProvider.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        return persistProvider.upsert(table, data, "id").thenApply(ok -> {
            // 持久化成功后从 pending 集合移除（ZREM 对不存在的 member 是 no-op）
            if (ok && redisProvider != null && redisProvider.isConnected()) {
                String cacheKey = buildCacheKey(table, key);
                redisProvider.zrem(PENDING_KEY, cacheKey)
                        .exceptionally(e -> {
                            log.warn("Failed to remove {} from pending: {}", cacheKey, e.getMessage());
                            return 0L;
                        });
            }
            return ok;
        });
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
    public CompletableFuture<Boolean> persistOnly(String table, String key) {
        String cacheKey = buildCacheKey(table, key);

        if (redisProvider == null || !redisProvider.isConnected()) {
            return CompletableFuture.completedFuture(true);
        }

        return redisProvider.get(cacheKey).thenCompose(cached -> {
            if (cached.isEmpty()) {
                // Redis key already expired or not found; remove from pending to avoid stale entries
                log.info("persistOnly: Redis key not found for {}/{}, removing from pending", table, key);
                redisProvider.zrem(PENDING_KEY, cacheKey).exceptionally(e -> {
                    log.warn("Failed to remove stale pending entry {}: {}", cacheKey, e.getMessage());
                    return 0L;
                });
                return CompletableFuture.completedFuture(false);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = gson.fromJson(cached.get(), Map.class);
            data.put("id", key);

            // 只持久化，不删除 Redis 缓存，保持缓存对在线玩家可用
            return saveToPersist(table, key, data);
        });
    }

    @Override
    public CompletableFuture<Boolean> delete(String table, String key) {
        String cacheKey = buildCacheKey(table, key);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        if (redisProvider != null && redisProvider.isConnected()) {
            futures.add(redisProvider.delete(cacheKey));
            // 删除时同步清除 pending 登记，避免 sweep 再去持久化已删除的数据
            redisProvider.zrem(PENDING_KEY, cacheKey);
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
// 在 dbManager.ensureTable 里

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

        // 停止 sweep 调度，不再接新任务
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

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
