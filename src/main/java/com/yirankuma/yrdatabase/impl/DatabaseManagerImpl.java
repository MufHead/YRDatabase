package com.yirankuma.yrdatabase.impl;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import com.yirankuma.yrdatabase.YRDatabase;
import com.yirankuma.yrdatabase.api.DatabaseManager;
import com.yirankuma.yrdatabase.config.DatabaseConfig;
import com.yirankuma.yrdatabase.mysql.MySQLManager;
import com.yirankuma.yrdatabase.redis.RedisManager;
import com.google.gson.Gson;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DatabaseManagerImpl implements DatabaseManager {

    private final RedisManager redisManager;
    private final MySQLManager mysqlManager;

    private final boolean redisEnabled;
    private final boolean mysqlEnabled;

    private final Gson gson = new Gson();

    // 缓存已创建的表，避免重复检查
    private final Set<String> createdTables = ConcurrentHashMap.newKeySet();

    public DatabaseManagerImpl(DatabaseConfig config) {
        this.redisManager = new RedisManager(config.getRedis());
        this.mysqlManager = new MySQLManager(config.getMysql());
        this.redisEnabled = config.getRedis().isEnabled();
        this.mysqlEnabled = config.getMysql().isEnabled();

        // 检查是否至少启用了一个数据库
        if (!redisEnabled && !mysqlEnabled) {
            throw new IllegalStateException("至少需要启用 Redis 或 MySQL 中的一个！");
        }
    }

    @Override
    public void initialize() {
        boolean hasValidConnection = false;

        if (redisEnabled) {
            redisManager.initialize();
            if (redisManager.isConnected()) {
                hasValidConnection = true;
            }
        }

        if (mysqlEnabled) {
            mysqlManager.initialize();
            if (mysqlManager.isConnected()) {
                hasValidConnection = true;
            }
        }

        if (!hasValidConnection) {
            // 显示警告而不是抛出异常
            YRDatabase.getInstance().getLogger().error("警告: 无法连接到任何数据库！");
            YRDatabase.getInstance().getLogger().error("请检查 Redis 或 MySQL 配置。");
            YRDatabase.getInstance().getLogger().error("系统将继续运行，但数据库功能将不可用。");
        }
    }

    @Override
    public void shutdown() {
        redisManager.shutdown();
        mysqlManager.shutdown();
    }

    @Override
    public boolean isRedisConnected() {
        return redisManager.isConnected();
    }

    @Override
    public boolean isMySQLConnected() {
        return mysqlManager.isConnected();
    }

    // ========== 通用数据操作 ==========

    @Override
    public CompletableFuture<String> get(String key) {
        // 如果启用Redis，优先从Redis获取
        if (redisEnabled && redisManager.isConnected()) {
            return redisManager.get(key).thenCompose(value -> {
                if (value != null) {
                    return CompletableFuture.completedFuture(value);
                }
                // Redis中没有，从MySQL获取
                return getFromMySQL(key);
            });
        }
        // Redis未启用或不可用，直接从MySQL获取
        return getFromMySQL(key);
    }

    @Override
    public CompletableFuture<Boolean> set(String key, String value, long expireSeconds) {
        CompletableFuture<Boolean> redisFuture = CompletableFuture.completedFuture(true);
        CompletableFuture<Boolean> mysqlFuture;

        // 如果启用Redis，优先设置到Redis
        if (redisEnabled && redisManager.isConnected()) {
            redisFuture = redisManager.set(key, value, expireSeconds);
        }

        // 同时设置到MySQL（作为持久化存储）
        if (mysqlManager.isConnected()) {
            mysqlFuture = setToMySQL(key, value);
        } else {
            mysqlFuture = CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> finalRedisFuture = redisFuture;
        CompletableFuture<Boolean> finalMysqlFuture = mysqlFuture;
        return CompletableFuture.allOf(redisFuture, mysqlFuture)
                .thenApply(v -> {
                    boolean redisSuccess = finalRedisFuture.join();
                    boolean mysqlSuccess = finalMysqlFuture.join();

                    // 如果Redis启用，至少Redis或MySQL成功即可
                    // 如果Redis未启用，必须MySQL成功
                    if (redisEnabled) {
                        return redisSuccess || mysqlSuccess;
                    } else {
                        return mysqlSuccess;
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> delete(String key) {
        CompletableFuture<Boolean> redisFuture = CompletableFuture.completedFuture(true);
        CompletableFuture<Boolean> mysqlFuture = CompletableFuture.completedFuture(true);

        // 如果启用Redis，从Redis删除
        if (redisEnabled && redisManager.isConnected()) {
            redisFuture = redisManager.delete(key);
        }

        // 从MySQL删除
        if (mysqlManager.isConnected()) {
            mysqlFuture = deleteFromMySQL(key);
        }

        CompletableFuture<Boolean> finalRedisFuture = redisFuture;
        CompletableFuture<Boolean> finalMysqlFuture = mysqlFuture;
        return CompletableFuture.allOf(redisFuture, mysqlFuture)
                .thenApply(v -> {
                    boolean redisSuccess = finalRedisFuture.join();
                    boolean mysqlSuccess = finalMysqlFuture.join();

                    // 如果Redis启用，至少Redis或MySQL成功即可
                    // 如果Redis未启用，必须MySQL成功
                    if (redisEnabled) {
                        return redisSuccess || mysqlSuccess;
                    } else {
                        return mysqlSuccess;
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        if (redisEnabled && redisManager.isConnected()) {
            return redisManager.exists(key).thenCompose(exists -> {
                if (exists) {
                    return CompletableFuture.completedFuture(true);
                }
                return existsInMySQL(key);
            });
        }
        return existsInMySQL(key);
    }


    // ========== Hash 操作 ==========

    @Override
    public CompletableFuture<String> hget(String key, String field) {
        if (redisEnabled && redisManager.isConnected()) {
            return redisManager.hget(key, field);
        }
        // Redis未启用时，Hash操作返回null（因为MySQL不直接支持Hash结构）
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> hset(String key, String field, String value) {
        if (redisEnabled && redisManager.isConnected()) {
            return redisManager.hset(key, field, value);
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Map<String, String>> hgetAll(String key) {
        if (redisEnabled && redisManager.isConnected()) {
            return redisManager.hgetAll(key);
        }
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    @Override
    public CompletableFuture<Boolean> hdel(String key, String field) {
        if (redisEnabled && redisManager.isConnected()) {
            return redisManager.hdel(key, field);
        }
        return CompletableFuture.completedFuture(false);
    }

    // ========== MySQL 表操作 ==========

    @Override
    public CompletableFuture<Boolean> createTable(String tableName, Map<String, String> columns) {
        return mysqlManager.createTable(tableName, columns);
    }

    @Override
    public CompletableFuture<Boolean> dropTable(String tableName) {
        return mysqlManager.dropTable(tableName);
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return mysqlManager.tableExists(tableName);
    }

    @Override
    public CompletableFuture<Boolean> insertIntoTable(String tableName, Map<String, Object> data) {
        return mysqlManager.insertIntoTable(tableName, data);
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> selectFromTable(String tableName, Map<String, Object> where) {
        return mysqlManager.selectFromTable(tableName, where);
    }

    @Override
    public CompletableFuture<Boolean> updateTable(String tableName, Map<String, Object> data, Map<String, Object> where) {
        return mysqlManager.updateTable(tableName, data, where);
    }

    @Override
    public CompletableFuture<Boolean> deleteFromTable(String tableName, Map<String, Object> where) {
        return mysqlManager.deleteFromTable(tableName, where);
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> executeQuery(String sql, Object... params) {
        return mysqlManager.executeQuery(sql, params);
    }

    @Override
    public CompletableFuture<Boolean> executeUpdate(String sql, Object... params) {
        return mysqlManager.executeUpdate(sql, params);
    }

    // ========== 私有辅助方法 ==========

    private CompletableFuture<String> getFromMySQL(String key) {
        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        Map<String, Object> where = Map.of("key_name", key);
        return mysqlManager.selectFromTable("yr_key_value", where)
                .thenApply(results -> {
                    if (!results.isEmpty()) {
                        return (String) results.get(0).get("value_data");
                    }
                    return null;
                });
    }

    private CompletableFuture<Boolean> setToMySQL(String key, String value) {
        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        // 先检查表是否存在，不存在则创建
        return mysqlManager.tableExists("yr_key_value").thenCompose(exists -> {
            if (!exists) {
                Map<String, String> columns = Map.of(
                        "key_name", "VARCHAR(255) PRIMARY KEY",
                        "value_data", "TEXT",
                        "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                        "updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                );
                return mysqlManager.createTable("yr_key_value", columns);
            }
            return CompletableFuture.completedFuture(true);
        }).thenCompose(created -> {
            Map<String, Object> data = Map.of("key_name", key, "value_data", value);
            Map<String, Object> where = Map.of("key_name", key);

            // 先尝试更新，如果没有记录则插入
            return mysqlManager.updateTable("yr_key_value", data, where)
                    .thenCompose(updated -> {
                        if (!updated) {
                            return mysqlManager.insertIntoTable("yr_key_value", data);
                        }
                        return CompletableFuture.completedFuture(true);
                    });
        });
    }

    private CompletableFuture<Boolean> deleteFromMySQL(String key) {
        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> where = Map.of("key_name", key);
        return mysqlManager.deleteFromTable("yr_key_value", where);
    }

    private CompletableFuture<Boolean> existsInMySQL(String key) {
        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> where = Map.of("key_name", key);
        return mysqlManager.selectFromTable("yr_key_value", where)
                .thenApply(results -> !results.isEmpty());
    }

    // ========== 智能API实现 ==========

    @Override
    public CompletableFuture<Map<String, Object>> smartGet(String tableName, String key, Map<String, String> tableSchema) {
        return ensureTable(tableName, tableSchema)
                .thenCompose(tableReady -> {
                    if (!tableReady) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // 1. 先尝试从Redis获取
                    if (redisManager.isConnected()) {
                        String redisKey = buildRedisKey(tableName, key);
                        return redisManager.get(redisKey)
                                .thenCompose(cachedData -> {
                                    if (cachedData != null) {
                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> data = gson.fromJson(cachedData, Map.class);
                                            return CompletableFuture.completedFuture(data);
                                        } catch (Exception e) {
                                            // JSON解析失败，从MySQL重新获取
                                            return getFromMySQLTable(tableName, key);
                                        }
                                    } else {
                                        // Redis中没有，从MySQL获取
                                        return getFromMySQLTable(tableName, key)
                                                .thenCompose(mysqlData -> {
                                                    if (mysqlData != null) {
                                                        // 将MySQL数据缓存到Redis
                                                        String jsonData = gson.toJson(mysqlData);
                                                        redisManager.set(redisKey, jsonData, 3600);
                                                    }
                                                    return CompletableFuture.completedFuture(mysqlData);
                                                });
                                    }
                                });
                    } else {
                        // Redis不可用，直接从MySQL获取
                        return getFromMySQLTable(tableName, key);
                    }
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> smartGet(String tableName, Player player, Map<String, String> tableSchema) {
        String key = YRDatabase.getInstance().resolvePlayerId(player);
        return ensureTable(tableName, tableSchema)
                .thenCompose(tableReady -> {
                    if (!tableReady) {
                        return CompletableFuture.completedFuture(null);
                    }

                    // 1. 先尝试从Redis获取
                    if (redisManager.isConnected()) {
                        String redisKey = buildRedisKey(tableName, key);
                        return redisManager.get(redisKey)
                                .thenCompose(cachedData -> {
                                    if (cachedData != null) {
                                        // Redis中有数据，反序列化返回
                                        try {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> data = gson.fromJson(cachedData, Map.class);
                                            return CompletableFuture.completedFuture(data);
                                        } catch (Exception e) {
                                            // JSON解析失败，从MySQL重新获取
                                            return getFromMySQLTable(tableName, key);
                                        }
                                    } else {
                                        // Redis中没有，从MySQL获取
                                        return getFromMySQLTable(tableName, key)
                                                .thenCompose(mysqlData -> {
                                                    if (mysqlData != null) {
                                                        // 将MySQL数据缓存到Redis
                                                        String jsonData = gson.toJson(mysqlData);
                                                        redisManager.set(redisKey, jsonData, 3600); // 默认1小时过期
                                                    }
                                                    return CompletableFuture.completedFuture(mysqlData);
                                                });
                                    }
                                });
                    } else {
                        // Redis不可用，直接从MySQL获取
                        return getFromMySQLTable(tableName, key);
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> smartSet(String tableName, String key, Map<String, Object> data, Map<String, String> tableSchema, long cacheExpireSeconds) {
        return ensureTable(tableName, tableSchema)
                .thenCompose(tableReady -> {
                    if (!tableReady) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // 准备数据（带主键）
                    Map<String, Object> dataWithKey = new HashMap<>(data);
                    String primaryKey = getPrimaryKeyColumn(tableSchema);
                    if (primaryKey != null) {
                        dataWithKey.put(primaryKey, key);
                    }

                    // 优先写入Redis
                    if (redisManager.isConnected()) {
                        String redisKey = buildRedisKey(tableName, key);
                        String jsonData = gson.toJson(data);
                        return redisManager.set(redisKey, jsonData, cacheExpireSeconds)
                                .thenApply(redisSuccess -> {
                                    if (redisSuccess) {
                                        // Redis写入成功即可返回成功
                                        return true;
                                    } else {
                                        // Redis失败则落盘到MySQL
                                        return saveToMySQL(tableName, key, dataWithKey, tableSchema).join();
                                    }
                                });
                    } else if (mysqlManager.isConnected()) {
                        // Redis不可用时直接写MySQL
                        return saveToMySQL(tableName, key, dataWithKey, tableSchema);
                    } else {
                        return CompletableFuture.completedFuture(false);
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> smartSet(String tableName, Player player, Map<String, Object> data, Map<String, String> tableSchema, long cacheExpireSeconds) {
        String key = YRDatabase.getInstance().resolvePlayerId(player);

        return ensureTable(tableName, tableSchema)
                .thenCompose(tableReady -> {
                    if (!tableReady) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // 准备数据
                    Map<String, Object> dataWithKey = new HashMap<>(data);
                    String primaryKey = getPrimaryKeyColumn(tableSchema);
                    if (primaryKey != null) {
                        dataWithKey.put(primaryKey, key);
                    }

                    // 优先尝试存储到 Redis
                    if (redisManager.isConnected()) {
                        String redisKey = buildRedisKey(tableName, key);
                        String jsonData = gson.toJson(data);
                        return redisManager.set(redisKey, jsonData, cacheExpireSeconds)
                                .thenApply(redisSuccess -> {
                                    if (redisSuccess) {
                                        // Redis 存储成功，不立即存储到 MySQL
                                        return true;
                                    } else {
                                        // Redis 存储失败，尝试存储到 MySQL
                                        return saveToMySQL(tableName, key, dataWithKey, tableSchema).join();
                                    }
                                });
                    } else if (mysqlManager.isConnected()) {
                        // Redis 不可用但 MySQL 可用，直接存储到 MySQL
                        return saveToMySQL(tableName, key, dataWithKey, tableSchema);
                    } else {
                        // 两个数据库都不可用
                        return CompletableFuture.completedFuture(false);
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> smartDelete(String tableName, String key) {
        CompletableFuture<Boolean> redisFuture = CompletableFuture.completedFuture(true);
        CompletableFuture<Boolean> mysqlFuture = CompletableFuture.completedFuture(true);

        // 1. Redis 删除
        if (redisManager.isConnected()) {
            String redisKey = buildRedisKey(tableName, key);
            redisFuture = redisManager.delete(redisKey);
        }

        // 2. MySQL 删除
        if (mysqlManager.isConnected()) {
            mysqlFuture = getTablePrimaryKey(tableName)
                    .thenCompose(primaryKey -> {
                        if (primaryKey != null) {
                            Map<String, Object> whereCondition = Map.of(primaryKey, key);
                            return mysqlManager.deleteFromTable(tableName, whereCondition);
                        }
                        return CompletableFuture.completedFuture(false);
                    });
        }

        CompletableFuture<Boolean> finalRedisFuture = redisFuture;
        CompletableFuture<Boolean> finalMysqlFuture = mysqlFuture;
        return CompletableFuture.allOf(redisFuture, mysqlFuture)
                .thenApply(v -> finalRedisFuture.join() || finalMysqlFuture.join());
    }

    @Override
    public CompletableFuture<Boolean> smartDelete(String tableName, Player player) {

        String key = YRDatabase.getInstance().resolvePlayerId(player);

        CompletableFuture<Boolean> redisFuture = CompletableFuture.completedFuture(true);
        CompletableFuture<Boolean> mysqlFuture = CompletableFuture.completedFuture(true);

        // 1. 从Redis删除
        if (redisManager.isConnected()) {
            String redisKey = buildRedisKey(tableName, key);
            redisFuture = redisManager.delete(redisKey);
        }

        // 2. 从MySQL删除
        if (mysqlManager.isConnected()) {
            // 需要先获取表的主键列名
            mysqlFuture = getTablePrimaryKey(tableName)
                    .thenCompose(primaryKey -> {
                        if (primaryKey != null) {
                            Map<String, Object> whereCondition = Map.of(primaryKey, key);
                            return mysqlManager.deleteFromTable(tableName, whereCondition);
                        }
                        return CompletableFuture.completedFuture(false);
                    });
        }

        CompletableFuture<Boolean> finalRedisFuture = redisFuture;
        CompletableFuture<Boolean> finalMysqlFuture = mysqlFuture;
        return CompletableFuture.allOf(redisFuture, mysqlFuture)
                .thenApply(v -> finalRedisFuture.join() || finalMysqlFuture.join());
    }

    @Override
    public CompletableFuture<Map<String, Map<String, Object>>> smartBatchGet(String tableName, List<String> keys, Map<String, String> tableSchema) {
        return ensureTable(tableName, tableSchema)
                .thenCompose(tableReady -> {
                    if (!tableReady || keys.isEmpty()) {
                        return CompletableFuture.completedFuture(new HashMap<>());
                    }

                    Map<String, Map<String, Object>> result = new HashMap<>();
                    List<String> missingKeys = new ArrayList<>();

                    if (redisManager.isConnected()) {
                        // 批量从Redis获取
                        List<CompletableFuture<Void>> redisFutures = keys.stream()
                                .map(key -> {
                                    String redisKey = buildRedisKey(tableName, key);
                                    return redisManager.get(redisKey)
                                            .thenAccept(cachedData -> {
                                                if (cachedData != null) {
                                                    try {
                                                        @SuppressWarnings("unchecked")
                                                        Map<String, Object> data = gson.fromJson(cachedData, Map.class);
                                                        synchronized (result) {
                                                            result.put(key, data);
                                                        }
                                                    } catch (Exception e) {
                                                        synchronized (missingKeys) {
                                                            missingKeys.add(key);
                                                        }
                                                    }
                                                } else {
                                                    synchronized (missingKeys) {
                                                        missingKeys.add(key);
                                                    }
                                                }
                                            });
                                })
                                .collect(Collectors.toList());

                        return CompletableFuture.allOf(redisFutures.toArray(new CompletableFuture[0]))
                                .thenCompose(v -> {
                                    if (missingKeys.isEmpty()) {
                                        return CompletableFuture.completedFuture(result);
                                    }

                                    // 从MySQL获取缺失的数据
                                    return batchGetFromMySQL(tableName, missingKeys, tableSchema)
                                            .thenApply(mysqlData -> {
                                                result.putAll(mysqlData);

                                                // 将MySQL数据缓存到Redis
                                                if (redisManager.isConnected()) {
                                                    mysqlData.forEach((k, value) -> {
                                                        String redisKey = buildRedisKey(tableName, k);
                                                        String jsonData = gson.toJson(value);
                                                        redisManager.set(redisKey, jsonData, 3600);
                                                    });
                                                }

                                                return result;
                                            });
                                });
                    } else {
                        // Redis不可用，直接从MySQL批量获取
                        return batchGetFromMySQL(tableName, keys, tableSchema);
                    }
                });
    }

    @Override
    public CompletableFuture<Boolean> smartBatchSet(String tableName, Map<String, Map<String, Object>> dataMap, Map<String, String> tableSchema, long cacheExpireSeconds) {
        return ensureTable(tableName, tableSchema)
                .thenCompose(tableReady -> {
                    if (!tableReady || dataMap.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    String primaryKey = getPrimaryKeyColumn(tableSchema);

                    // 批量更新MySQL
                    List<CompletableFuture<Boolean>> mysqlFutures = dataMap.entrySet().stream()
                            .map(entry -> {
                                String key = entry.getKey();
                                Map<String, Object> data = new HashMap<>(entry.getValue());
                                if (primaryKey != null) {
                                    data.put(primaryKey, key);
                                }

                                Map<String, Object> whereCondition = primaryKey != null ?
                                        Map.of(primaryKey, key) : new HashMap<>();

                                return mysqlManager.selectFromTable(tableName, whereCondition)
                                        .thenCompose(existingRows -> {
                                            if (!existingRows.isEmpty()) {
                                                return mysqlManager.updateTable(tableName, data, whereCondition);
                                            } else {
                                                return mysqlManager.insertIntoTable(tableName, data);
                                            }
                                        });
                            })
                            .collect(Collectors.toList());

                    // 批量更新Redis缓存
                    List<CompletableFuture<Boolean>> redisFutures = new ArrayList<>();
                    if (redisManager.isConnected()) {
                        redisFutures = dataMap.entrySet().stream()
                                .map(entry -> {
                                    String key = entry.getKey();
                                    Map<String, Object> data = entry.getValue();
                                    String redisKey = buildRedisKey(tableName, key);
                                    String jsonData = gson.toJson(data);
                                    return redisManager.set(redisKey, jsonData, cacheExpireSeconds);
                                })
                                .collect(Collectors.toList());
                    }

                    List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
                    allFutures.addAll(mysqlFutures);
                    allFutures.addAll(redisFutures);

                    return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> allFutures.stream().allMatch(CompletableFuture::join));
                });
    }

    @Override
    public CompletableFuture<Boolean> ensureTable(String tableName, Map<String, String> tableSchema) {
        // 检查缓存中是否已确认表存在
        if (createdTables.contains(tableName)) {
            return CompletableFuture.completedFuture(true);
        }

        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        return mysqlManager.tableExists(tableName)
                .thenCompose(exists -> {
                    if (exists) {
                        createdTables.add(tableName);
                        return CompletableFuture.completedFuture(true);
                    } else {
                        // 表不存在，创建表
                        return mysqlManager.createTable(tableName, tableSchema)
                                .thenApply(created -> {
                                    if (created) {
                                        createdTables.add(tableName);
                                    }
                                    return created;
                                });
                    }
                });
    }

    @Override
    public CompletableFuture<Integer> preloadToCache(String tableName, String keyColumn, long expireSeconds) {
        if (!mysqlManager.isConnected() || !redisManager.isConnected()) {
            return CompletableFuture.completedFuture(0);
        }

        return mysqlManager.selectFromTable(tableName, null)
                .thenCompose(rows -> {
                    if (rows.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }

                    List<CompletableFuture<Boolean>> cacheFutures = rows.stream()
                            .map(row -> {
                                Object keyValue = row.get(keyColumn);
                                if (keyValue != null) {
                                    String redisKey = buildRedisKey(tableName, keyValue.toString());
                                    String jsonData = gson.toJson(row);
                                    return redisManager.set(redisKey, jsonData, expireSeconds);
                                }
                                return CompletableFuture.completedFuture(false);
                            })
                            .collect(Collectors.toList());

                    return CompletableFuture.allOf(cacheFutures.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> (int) cacheFutures.stream().mapToLong(f -> f.join() ? 1 : 0).sum());
                });
    }

    // ========== 私有辅助方法 ==========


    // 辅助方法：保存到 MySQL
    private CompletableFuture<Boolean> saveToMySQL(String tableName, String key, Map<String, Object> dataWithKey, Map<String, String> tableSchema) {
        String primaryKey = getPrimaryKeyColumn(tableSchema);
        Map<String, Object> whereCondition = primaryKey != null ?
                Map.of(primaryKey, key) : new HashMap<>();

        return mysqlManager.selectFromTable(tableName, whereCondition)
                .thenCompose(existingRows -> {
                    if (!existingRows.isEmpty()) {
                        // 记录存在，执行更新
                        return mysqlManager.updateTable(tableName, dataWithKey, whereCondition);
                    } else {
                        // 记录不存在，执行插入
                        return mysqlManager.insertIntoTable(tableName, dataWithKey);
                    }
                });
    }
    private String buildRedisKey(String tableName, String key) {
        return "table:" + tableName + ":" + key;
    }

    private CompletableFuture<Map<String, Object>> getFromMySQLTable(String tableName, String key) {
        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        return getTablePrimaryKey(tableName)
                .thenCompose(primaryKey -> {
                    if (primaryKey == null) {
                        return CompletableFuture.completedFuture(null);
                    }

                    Map<String, Object> whereCondition = Map.of(primaryKey, key);
                    return mysqlManager.selectFromTable(tableName, whereCondition)
                            .thenApply(rows -> rows.isEmpty() ? null : rows.get(0));
                });
    }

    private CompletableFuture<Map<String, Map<String, Object>>> batchGetFromMySQL(String tableName, List<String> keys, Map<String, String> tableSchema) {
        if (!mysqlManager.isConnected() || keys.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        String primaryKey = getPrimaryKeyColumn(tableSchema);
        if (primaryKey == null) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        // 构建IN查询
        String placeholders = keys.stream().map(k -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM `" + tableName + "` WHERE `" + primaryKey + "` IN (" + placeholders + ")";

        return mysqlManager.executeQuery(sql, keys.toArray())
                .thenApply(rows -> {
                    Map<String, Map<String, Object>> result = new HashMap<>();
                    for (Map<String, Object> row : rows) {
                        Object keyValue = row.get(primaryKey);
                        if (keyValue != null) {
                            result.put(keyValue.toString(), row);
                        }
                    }
                    return result;
                });
    }

    private CompletableFuture<String> getTablePrimaryKey(String tableName) {
        if (!mysqlManager.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }

        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_KEY = 'PRI' LIMIT 1";

        return mysqlManager.executeQuery(sql, mysqlManager.getConfig().getDatabase(), tableName)
                .thenApply(rows -> {
                    if (!rows.isEmpty()) {
                        return (String) rows.get(0).get("COLUMN_NAME");
                    }
                    return null;
                });
    }

    private String getPrimaryKeyColumn(Map<String, String> tableSchema) {
        return tableSchema.entrySet().stream()
                .filter(entry -> entry.getValue().toUpperCase().contains("PRIMARY KEY"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // ========== 数据持久化实现 ==========

    @Override
    /**
     * 将Redis缓存数据持久化到MySQL并删除Redis缓存
     * @param tableName 表名
     * @param key 主键值
     * @param tableSchema 表结构定义
     * @return 是否成功
     */
    public CompletableFuture<Boolean> persistAndClearCache(String tableName, String key, Map<String, String> tableSchema) {
        if (!redisManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        String redisKey = buildRedisKey(tableName, key);
        return redisManager.get(redisKey)
                .thenCompose(cachedData -> {
                    if (cachedData != null) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = gson.fromJson(cachedData, Map.class);

                            // 如果MySQL可用，先持久化到MySQL
                            if (mysqlManager.isConnected()) {
                                Map<String, Object> dataWithKey = new HashMap<>(data);
                                String primaryKey = getPrimaryKeyColumn(tableSchema);
                                if (primaryKey != null) {
                                    dataWithKey.put(primaryKey, key);
                                }

                                return saveToMySQL(tableName, key, dataWithKey, tableSchema)
                                        .thenCompose(mysqlSuccess -> {
                                            if (mysqlSuccess) {
                                                // MySQL保存成功，删除Redis缓存
                                                return redisManager.delete(redisKey);
                                            } else {
                                                // MySQL保存失败，不删除缓存
                                                return CompletableFuture.completedFuture(false);
                                            }
                                        });
                            } else {
                                // MySQL不可用，只删除Redis缓存
                                return redisManager.delete(redisKey);
                            }
                        } catch (Exception e) {
                            return CompletableFuture.completedFuture(false);
                        }
                    } else {
                        // 缓存中没有数据，返回true（认为操作成功）
                        return CompletableFuture.completedFuture(true);
                    }
                });
    }
    public CompletableFuture<Boolean> persistAndClearCache(String tableName, Player player, Map<String, String> tableSchema) {
        if (!redisManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        String key = YRDatabase.getInstance().resolvePlayerId(player);

        String redisKey = buildRedisKey(tableName, key);
        return redisManager.get(redisKey)
                .thenCompose(cachedData -> {
                    if (cachedData != null) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = gson.fromJson(cachedData, Map.class);

                            // 如果MySQL可用，先持久化到MySQL
                            if (mysqlManager.isConnected()) {
                                Map<String, Object> dataWithKey = new HashMap<>(data);
                                String primaryKey = getPrimaryKeyColumn(tableSchema);
                                if (primaryKey != null) {
                                    dataWithKey.put(primaryKey, key);
                                }

                                return saveToMySQL(tableName, key, dataWithKey, tableSchema)
                                        .thenCompose(mysqlSuccess -> {
                                            if (mysqlSuccess) {
                                                // MySQL保存成功，删除Redis缓存
                                                return redisManager.delete(redisKey);
                                            } else {
                                                // MySQL保存失败，不删除缓存
                                                return CompletableFuture.completedFuture(false);
                                            }
                                        });
                            } else {
                                // MySQL不可用，只删除Redis缓存
                                return redisManager.delete(redisKey);
                            }
                        } catch (Exception e) {
                            return CompletableFuture.completedFuture(false);
                        }
                    } else {
                        // 缓存中没有数据，返回true（认为操作成功）
                        return CompletableFuture.completedFuture(true);
                    }
                });
    }

    @Override
    public CompletableFuture<Integer> batchPersistAndClearCache(String tableName, List<String> keys, Map<String, String> tableSchema) {
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        List<CompletableFuture<Boolean>> persistFutures = keys.stream()
                .map(key -> persistAndClearCache(tableName, key, tableSchema))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(persistFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> (int) persistFutures.stream().mapToLong(f -> f.join() ? 1 : 0).sum());
    }

    @Override
    public CompletableFuture<Integer> persistAllCacheForTable(String tableName, Map<String, String> tableSchema) {
        if (!redisManager.isConnected()) {
            return CompletableFuture.completedFuture(0);
        }

        // 获取所有匹配的Redis键
        String pattern = "table:" + tableName + ":*";
        return getAllRedisKeys(pattern)
                .thenCompose(redisKeys -> {
                    if (redisKeys.isEmpty()) {
                        return CompletableFuture.completedFuture(0);
                    }

                    // 提取实际的key值
                    List<String> keys = redisKeys.stream()
                            .map(redisKey -> redisKey.substring(("table:" + tableName + ":").length()))
                            .collect(Collectors.toList());

                    return batchPersistAndClearCache(tableName, keys, tableSchema);
                });
    }

    @Override
    public CompletableFuture<Boolean> persistPlayerData(String playerId, Map<String, Map<String, String>> tableSchemas) {
        List<CompletableFuture<Boolean>> persistFutures = tableSchemas.entrySet().stream()
                .map(entry -> {
                    String tableName = entry.getKey();
                    Map<String, String> tableSchema = entry.getValue();
                    return persistAndClearCache(tableName, playerId, tableSchema);
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(persistFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> persistFutures.stream().allMatch(CompletableFuture::join));
    }

    @Override
    public CompletableFuture<Boolean> persistPlayerData(Player player, Map<String, Map<String, String>> tableSchemas) {

        String playerId = YRDatabase.getInstance().resolvePlayerId(player);

        List<CompletableFuture<Boolean>> persistFutures = tableSchemas.entrySet().stream()
                .map(entry -> {
                    String tableName = entry.getKey();
                    Map<String, String> tableSchema = entry.getValue();
                    return persistAndClearCache(tableName, playerId, tableSchema);
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(persistFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> persistFutures.stream().allMatch(CompletableFuture::join));
    }

    @Override
    public CompletableFuture<Boolean> clearCache(String tableName, String key) {
        if (!redisManager.isConnected()) {
            return CompletableFuture.completedFuture(false);
        }

        String redisKey = buildRedisKey(tableName, key);
        return redisManager.delete(redisKey);
    }

    @Override
    public CompletableFuture<Integer> batchClearCache(String tableName, List<String> keys) {
        if (!redisManager.isConnected() || keys.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        List<CompletableFuture<Boolean>> clearFutures = keys.stream()
                .map(key -> clearCache(tableName, key))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(clearFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> (int) clearFutures.stream().mapToLong(f -> f.join() ? 1 : 0).sum());
    }

    // ========== 私有辅助方法 ==========

    private CompletableFuture<List<String>> getAllRedisKeys(String pattern) {
        if (!redisManager.isConnected()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        // 注意：这里需要在RedisManager中添加keys方法
        return redisManager.keys(pattern);
    }
}