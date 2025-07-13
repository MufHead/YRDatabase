package com.yirankuma.yrdatabase.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DatabaseManager {
    
    // ========== 通用数据操作 ==========
    
    /**
     * 获取数据 (优先Redis，其次MySQL)
     * @param key 键
     * @return 值
     */
    CompletableFuture<String> get(String key);
    
    /**
     * 设置数据 (优先Redis缓存，同时更新MySQL)
     * @param key 键
     * @param value 值
     * @param expireSeconds 过期时间(秒)，-1表示永不过期
     */
    CompletableFuture<Boolean> set(String key, String value, long expireSeconds);
    
    /**
     * 删除数据
     * @param key 键
     */
    CompletableFuture<Boolean> delete(String key);
    
    /**
     * 检查键是否存在
     * @param key 键
     */
    CompletableFuture<Boolean> exists(String key);
    
    // ========== Hash 操作 ==========
    
    /**
     * 获取Hash字段值
     * @param key Hash键
     * @param field 字段
     */
    CompletableFuture<String> hget(String key, String field);
    
    /**
     * 设置Hash字段值
     * @param key Hash键
     * @param field 字段
     * @param value 值
     */
    CompletableFuture<Boolean> hset(String key, String field, String value);
    
    /**
     * 获取Hash所有字段
     * @param key Hash键
     */
    CompletableFuture<Map<String, String>> hgetAll(String key);
    
    /**
     * 删除Hash字段
     * @param key Hash键
     * @param field 字段
     */
    CompletableFuture<Boolean> hdel(String key, String field);
    
    // ========== MySQL 表操作 ==========
    
    /**
     * 创建表
     * @param tableName 表名
     * @param columns 列定义 (列名 -> 类型)
     */
    CompletableFuture<Boolean> createTable(String tableName, Map<String, String> columns);
    
    /**
     * 删除表
     * @param tableName 表名
     */
    CompletableFuture<Boolean> dropTable(String tableName);
    
    /**
     * 检查表是否存在
     * @param tableName 表名
     */
    CompletableFuture<Boolean> tableExists(String tableName);
    
    /**
     * 插入数据到表
     * @param tableName 表名
     * @param data 数据 (列名 -> 值)
     */
    CompletableFuture<Boolean> insertIntoTable(String tableName, Map<String, Object> data);
    
    /**
     * 从表中查询数据
     * @param tableName 表名
     * @param where 条件 (列名 -> 值)
     */
    CompletableFuture<List<Map<String, Object>>> selectFromTable(String tableName, Map<String, Object> where);
    
    /**
     * 更新表数据
     * @param tableName 表名
     * @param data 更新数据 (列名 -> 值)
     * @param where 条件 (列名 -> 值)
     */
    CompletableFuture<Boolean> updateTable(String tableName, Map<String, Object> data, Map<String, Object> where);
    
    /**
     * 从表中删除数据
     * @param tableName 表名
     * @param where 条件 (列名 -> 值)
     */
    CompletableFuture<Boolean> deleteFromTable(String tableName, Map<String, Object> where);
    
    /**
     * 执行自定义SQL语句
     * @param sql SQL语句
     * @param params 参数
     */
    CompletableFuture<List<Map<String, Object>>> executeQuery(String sql, Object... params);
    
    /**
     * 执行自定义更新SQL语句
     * @param sql SQL语句
     * @param params 参数
     */
    CompletableFuture<Boolean> executeUpdate(String sql, Object... params);
    
    // ========== 连接管理 ==========
    
    /**
     * 初始化连接
     */
    void initialize();
    
    /**
     * 关闭连接
     */
    void shutdown();
    
    /**
     * 检查Redis连接状态
     */
    boolean isRedisConnected();
    
    /**
     * 检查MySQL连接状态
     */
    boolean isMySQLConnected();
    
    // ========== 智能表管理API ==========
    
    /**
     * 智能获取数据 - 自动检测表并创建，优先从Redis读取，其次MySQL
     * @param tableName 表名
     * @param key 主键值
     * @param tableSchema 表结构定义（如果表不存在时使用）
     * @return 数据行
     */
    CompletableFuture<Map<String, Object>> smartGet(String tableName, String key, Map<String, String> tableSchema);
    
    /**
     * 智能设置数据 - 自动检测表并创建，智能选择存储位置
     * @param tableName 表名
     * @param key 主键值
     * @param data 数据
     * @param tableSchema 表结构定义（如果表不存在时使用）
     * @param cacheExpireSeconds Redis缓存过期时间（秒），-1表示永不过期
     * @return 是否成功
     */
    CompletableFuture<Boolean> smartSet(String tableName, String key, Map<String, Object> data, Map<String, String> tableSchema, long cacheExpireSeconds);
    
    /**
     * 智能删除数据 - 同时从Redis和MySQL删除
     * @param tableName 表名
     * @param key 主键值
     * @return 是否成功
     */
    CompletableFuture<Boolean> smartDelete(String tableName, String key);
    
    /**
     * 智能批量获取数据
     * @param tableName 表名
     * @param keys 主键列表
     * @param tableSchema 表结构定义
     * @return 数据映射 (key -> data)
     */
    CompletableFuture<Map<String, Map<String, Object>>> smartBatchGet(String tableName, List<String> keys, Map<String, String> tableSchema);
    
    /**
     * 智能批量设置数据
     * @param tableName 表名
     * @param dataMap 数据映射 (key -> data)
     * @param tableSchema 表结构定义
     * @param cacheExpireSeconds Redis缓存过期时间
     * @return 是否成功
     */
    CompletableFuture<Boolean> smartBatchSet(String tableName, Map<String, Map<String, Object>> dataMap, Map<String, String> tableSchema, long cacheExpireSeconds);
    
    /**
     * 确保表存在，不存在则创建
     * @param tableName 表名
     * @param tableSchema 表结构定义
     * @return 是否成功
     */
    CompletableFuture<Boolean> ensureTable(String tableName, Map<String, String> tableSchema);
    
    /**
     * 从MySQL预加载数据到Redis缓存
     * @param tableName 表名
     * @param keyColumn 主键列名
     * @param expireSeconds 缓存过期时间
     * @return 预加载的数据条数
     */
    CompletableFuture<Integer> preloadToCache(String tableName, String keyColumn, long expireSeconds);
    
    // ========== 数据持久化API ==========

    /**
     * 将Redis缓存数据持久化到MySQL并删除Redis缓存
     * @param tableName 表名
     * @param key 主键值
     * @param tableSchema 表结构定义
     * @return 是否成功
     */
    CompletableFuture<Boolean> persistAndClearCache(String tableName, String key, Map<String, String> tableSchema);
    
    /**
     * 批量将Redis缓存数据持久化到MySQL并删除Redis缓存
     * @param tableName 表名
     * @param keys 主键列表
     * @param tableSchema 表结构定义
     * @return 成功持久化的数量
     */
    CompletableFuture<Integer> batchPersistAndClearCache(String tableName, List<String> keys, Map<String, String> tableSchema);
    
    /**
     * 将指定表的所有Redis缓存数据持久化到MySQL并清理缓存
     * @param tableName 表名
     * @param tableSchema 表结构定义
     * @return 成功持久化的数量
     */
    CompletableFuture<Integer> persistAllCacheForTable(String tableName, Map<String, String> tableSchema);
    
    /**
     * 玩家下线时的数据持久化（专门为玩家数据设计）
     * @param playerId 玩家ID
     * @param tableSchemas 相关表的结构定义 (表名 -> 表结构)
     * @return 是否成功
     */
    CompletableFuture<Boolean> persistPlayerData(String playerId, Map<String, Map<String, String>> tableSchemas);
    
    /**
     * 只清理Redis缓存，不持久化
     * @param tableName 表名
     * @param key 主键值
     * @return 是否成功
     */
    CompletableFuture<Boolean> clearCache(String tableName, String key);
    
    /**
     * 批量清理Redis缓存
     * @param tableName 表名
     * @param keys 主键列表
     * @return 成功清理的数量
     */
    CompletableFuture<Integer> batchClearCache(String tableName, List<String> keys);
}