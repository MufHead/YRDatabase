package com.yirankuma.yrdatabase.api.provider;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Persistence provider interface with SQL-specific operations.
 * Typically implemented by MySQL or SQLite.
 *
 * @author YiranKuma
 */
public interface PersistProvider extends StorageProvider {

    // ==================== Table Operations ====================

    /**
     * Create table if not exists.
     *
     * @param tableName Table name
     * @param schema    Column definitions (column name -> SQL type)
     * @return Success status
     */
    CompletableFuture<Boolean> createTable(String tableName, Map<String, String> schema);

    /**
     * Check if table exists.
     *
     * @param tableName Table name
     * @return Existence status
     */
    CompletableFuture<Boolean> tableExists(String tableName);

    /**
     * Drop table if exists.
     *
     * @param tableName Table name
     * @return Success status
     */
    CompletableFuture<Boolean> dropTable(String tableName);

    // ==================== Query Operations ====================

    /**
     * Query data from table with conditions.
     *
     * @param table      Table name
     * @param conditions Field-value conditions (AND)
     * @return List of matching rows
     */
    CompletableFuture<List<Map<String, Object>>> query(String table, Map<String, Object> conditions);

    /**
     * Query data with custom WHERE clause.
     *
     * @param table       Table name
     * @param whereClause WHERE clause (without "WHERE" keyword)
     * @param params      Prepared statement parameters
     * @return List of matching rows
     */
    CompletableFuture<List<Map<String, Object>>> query(String table, String whereClause, Object... params);

    /**
     * Query all data from table.
     *
     * @param table Table name
     * @return List of all rows
     */
    CompletableFuture<List<Map<String, Object>>> queryAll(String table);

    /**
     * Count rows in table.
     *
     * @param table      Table name
     * @param conditions Field-value conditions (AND)
     * @return Number of matching rows
     */
    CompletableFuture<Long> count(String table, Map<String, Object> conditions);

    /**
     * Count all rows in table.
     *
     * @param table Table name
     * @return Number of rows
     */
    CompletableFuture<Long> countAll(String table);

    // ==================== Insert/Update Operations ====================

    /**
     * Insert data into table.
     *
     * @param table Table name
     * @param data  Column-value pairs
     * @return Success status
     */
    CompletableFuture<Boolean> insert(String table, Map<String, Object> data);

    /**
     * Insert or update data (upsert).
     *
     * @param table      Table name
     * @param data       Column-value pairs
     * @param primaryKey Primary key column name
     * @return Success status
     */
    CompletableFuture<Boolean> upsert(String table, Map<String, Object> data, String primaryKey);

    /**
     * Update data in table.
     *
     * @param table      Table name
     * @param data       Column-value pairs to update
     * @param conditions WHERE conditions
     * @return Number of affected rows
     */
    CompletableFuture<Integer> update(String table, Map<String, Object> data, Map<String, Object> conditions);

    /**
     * Delete data from table.
     *
     * @param table      Table name
     * @param conditions WHERE conditions
     * @return Number of affected rows
     */
    CompletableFuture<Integer> deleteWhere(String table, Map<String, Object> conditions);

    // ==================== Raw SQL ====================

    /**
     * Execute raw query SQL.
     *
     * @param sql    SQL statement
     * @param params Prepared statement parameters
     * @return Query results
     */
    CompletableFuture<List<Map<String, Object>>> executeQuery(String sql, Object... params);

    /**
     * Execute raw update SQL.
     *
     * @param sql    SQL statement
     * @param params Prepared statement parameters
     * @return Number of affected rows
     */
    CompletableFuture<Integer> executeUpdate(String sql, Object... params);

    // ==================== Transaction Support ====================

    /**
     * Execute operations within a transaction.
     *
     * @param action Action to perform with connection
     * @param <R>    Return type
     * @return Result of the action
     */
    <R> CompletableFuture<R> transaction(Function<Connection, R> action);

    // ==================== Batch Operations ====================

    /**
     * Insert multiple rows in batch.
     *
     * @param table Table name
     * @param rows  List of row data
     * @return Success status
     */
    CompletableFuture<Boolean> batchInsert(String table, List<Map<String, Object>> rows);
}
