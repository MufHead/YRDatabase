package com.yirankuma.yrdatabase.core.provider.mysql;

import com.yirankuma.yrdatabase.api.config.DatabaseConfig;
import com.yirankuma.yrdatabase.api.provider.PersistProvider;
import com.yirankuma.yrdatabase.api.provider.ProviderType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * MySQL persistence provider implementation using HikariCP.
 *
 * @author YiranKuma
 */
@Slf4j
public class MySQLProvider implements PersistProvider {

    private final DatabaseConfig.PersistConfig.MySQLConfig config;
    private HikariDataSource dataSource;
    private ExecutorService executor;
    private volatile boolean connected = false;
    private final Set<String> createdTables = new HashSet<>();

    public MySQLProvider(DatabaseConfig.PersistConfig.MySQLConfig config) {
        this.config = config;
    }

    /**
     * Initialize the MySQL connection pool.
     *
     * @return Completion future
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                HikariConfig hikariConfig = new HikariConfig();
                String driverClass = resolveDriverClass();
                hikariConfig.setDriverClassName(driverClass);
                hikariConfig.setJdbcUrl(String.format(
                        "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=%s&characterEncoding=utf8&allowPublicKeyRetrieval=true",
                        config.getHost(), config.getPort(), config.getDatabase(), config.getTimezone()
                ));
                hikariConfig.setUsername(config.getUsername());
                hikariConfig.setPassword(config.getPassword());
                hikariConfig.setMaximumPoolSize(config.getPool().getMaxSize());
                hikariConfig.setMinimumIdle(config.getPool().getMinIdle());
                hikariConfig.setConnectionTimeout(config.getPool().getConnectionTimeout());
                hikariConfig.setIdleTimeout(config.getPool().getIdleTimeout());
                hikariConfig.setMaxLifetime(config.getPool().getMaxLifetime());
                hikariConfig.setPoolName("YRDatabase-MySQL");

                // Performance optimizations
                hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
                hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
                hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
                hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");

                dataSource = new HikariDataSource(hikariConfig);
                executor = Executors.newFixedThreadPool(config.getPool().getMaxSize());

                // Test connection
                try (Connection conn = dataSource.getConnection()) {
                    connected = conn.isValid(5);
                }

                log.info("MySQL connected successfully to {}:{}/{} using driver {}",
                        config.getHost(), config.getPort(), config.getDatabase(), driverClass);
            } catch (Exception e) {
                log.error("Failed to connect to MySQL: {}", e.getMessage());
                connected = false;
                throw new RuntimeException("Failed to connect to MySQL", e);
            }
        });
    }

    private String resolveDriverClass() throws ClassNotFoundException {
        String[] candidates = {
            "com.mysql.cj.jdbc.Driver",
            "com.yirankuma.yrdatabase.libs.mysql.cj.jdbc.Driver"
        };
        for (String className : candidates) {
            try {
                Class.forName(className);
                return className;
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        throw new ClassNotFoundException("MySQL driver not found in classpath");
    }

    private <T> CompletableFuture<T> executeAsync(SqlFunction<T> function) {
        if (!connected || dataSource == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("MySQL not connected"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                return function.apply(conn);
            } catch (SQLException e) {
                log.error("MySQL operation failed: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection conn) throws SQLException;
    }

    // ==================== Basic Key-Value Operations (using table:key format) ====================

    @Override
    public CompletableFuture<Optional<String>> get(String key) {
        String[] parts = parseKey(key);
        String table = parts[0];
        String pk = parts[1];
        
        return query(table, Map.of("id", pk)).thenApply(results -> {
            if (results.isEmpty()) return Optional.empty();
            Object value = results.get(0).get("value");
            return Optional.ofNullable(value != null ? value.toString() : null);
        });
    }

    @Override
    public CompletableFuture<Boolean> set(String key, String value) {
        String[] parts = parseKey(key);
        String table = parts[0];
        String pk = parts[1];
        
        return upsert(table, Map.of("id", pk, "value", value), "id");
    }

    @Override
    public CompletableFuture<Boolean> setEx(String key, String value, Duration ttl) {
        // MySQL doesn't support TTL natively, just store normally
        return set(key, value);
    }

    @Override
    public CompletableFuture<Boolean> delete(String key) {
        String[] parts = parseKey(key);
        String table = parts[0];
        String pk = parts[1];
        
        return deleteWhere(table, Map.of("id", pk)).thenApply(count -> count > 0);
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        String[] parts = parseKey(key);
        String table = parts[0];
        String pk = parts[1];
        
        return count(table, Map.of("id", pk)).thenApply(count -> count > 0);
    }

    private String[] parseKey(String key) {
        int colonIndex = key.indexOf(':');
        if (colonIndex > 0) {
            return new String[]{key.substring(0, colonIndex), key.substring(colonIndex + 1)};
        }
        return new String[]{"kv_store", key};
    }

    // ==================== Batch Operations ====================

    @Override
    public CompletableFuture<Map<String, String>> mget(List<String> keys) {
        Map<String, CompletableFuture<Optional<String>>> futures = new HashMap<>();
        for (String key : keys) {
            futures.put(key, get(key));
        }
        
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<String, String> result = new HashMap<>();
                    futures.forEach((key, future) -> {
                        future.join().ifPresent(value -> result.put(key, value));
                    });
                    return result;
                });
    }

    @Override
    public CompletableFuture<Boolean> mset(Map<String, String> entries) {
        List<CompletableFuture<Boolean>> futures = entries.entrySet().stream()
                .map(e -> set(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().allMatch(f -> f.join()));
    }

    // ==================== Hash Operations (stored as JSON) ====================

    @Override
    public CompletableFuture<Optional<String>> hget(String key, String field) {
        return hgetAll(key).thenApply(map -> Optional.ofNullable(map.get(field)));
    }

    @Override
    public CompletableFuture<Boolean> hset(String key, String field, String value) {
        return hgetAll(key).thenCompose(existing -> {
            existing.put(field, value);
            return hmset(key, existing);
        });
    }

    @Override
    public CompletableFuture<Map<String, String>> hgetAll(String key) {
        return get(key).thenApply(opt -> {
            if (opt.isEmpty()) return new HashMap<>();
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                @SuppressWarnings("unchecked")
                Map<String, String> map = gson.fromJson(opt.get(), Map.class);
                return map != null ? new HashMap<>(map) : new HashMap<>();
            } catch (Exception e) {
                return new HashMap<>();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> hdel(String key, String... fields) {
        return hgetAll(key).thenCompose(existing -> {
            for (String field : fields) {
                existing.remove(field);
            }
            return hmset(key, existing);
        });
    }

    @Override
    public CompletableFuture<Boolean> hmset(String key, Map<String, String> fields) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String json = gson.toJson(fields);
        return set(key, json);
    }

    // ==================== Table Operations ====================

    @Override
    public CompletableFuture<Boolean> createTable(String tableName, Map<String, String> schema) {
        if (createdTables.contains(tableName)) {
            return CompletableFuture.completedFuture(true);
        }

        return executeAsync(conn -> {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `")
                    .append(tableName).append("` (");
            
            List<String> columns = new ArrayList<>();
            String primaryKey = null;
            
            for (Map.Entry<String, String> entry : schema.entrySet()) {
                String columnDef = "`" + entry.getKey() + "` " + entry.getValue();
                columns.add(columnDef);
                
                if (entry.getValue().toUpperCase().contains("PRIMARY KEY")) {
                    primaryKey = entry.getKey();
                }
            }
            
            sql.append(String.join(", ", columns));
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql.toString());
                createdTables.add(tableName);
                log.debug("Created table: {}", tableName);
                return true;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        if (createdTables.contains(tableName)) {
            return CompletableFuture.completedFuture(true);
        }

        return executeAsync(conn -> {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
                boolean exists = rs.next();
                if (exists) {
                    createdTables.add(tableName);
                }
                return exists;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> dropTable(String tableName) {
        return executeAsync(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS `" + tableName + "`");
                createdTables.remove(tableName);
                return true;
            }
        });
    }

    // ==================== Query Operations ====================

    @Override
    public CompletableFuture<List<Map<String, Object>>> query(String table, Map<String, Object> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return queryAll(table);
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM `").append(table).append("` WHERE ");
        List<Object> params = new ArrayList<>();
        List<String> clauses = new ArrayList<>();

        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            clauses.add("`" + entry.getKey() + "` = ?");
            params.add(entry.getValue());
        }

        sql.append(String.join(" AND ", clauses));
        return executeQuery(sql.toString(), params.toArray());
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> query(String table, String whereClause, Object... params) {
        String sql = "SELECT * FROM `" + table + "` WHERE " + whereClause;
        return executeQuery(sql, params);
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> queryAll(String table) {
        return executeQuery("SELECT * FROM `" + table + "`");
    }

    @Override
    public CompletableFuture<Long> count(String table, Map<String, Object> conditions) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM `").append(table).append("`");
        List<Object> params = new ArrayList<>();

        if (conditions != null && !conditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> clauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                clauses.add("`" + entry.getKey() + "` = ?");
                params.add(entry.getValue());
            }
            sql.append(String.join(" AND ", clauses));
        }

        return executeQuery(sql.toString(), params.toArray())
                .thenApply(results -> {
                    if (results.isEmpty()) return 0L;
                    Object count = results.get(0).values().iterator().next();
                    return ((Number) count).longValue();
                });
    }

    @Override
    public CompletableFuture<Long> countAll(String table) {
        return count(table, null);
    }

    // ==================== Insert/Update Operations ====================

    @Override
    public CompletableFuture<Boolean> insert(String table, Map<String, Object> data) {
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(table).append("` (");
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            columns.add("`" + entry.getKey() + "`");
            placeholders.add("?");
            values.add(entry.getValue());
        }

        sql.append(String.join(", ", columns))
                .append(") VALUES (")
                .append(String.join(", ", placeholders))
                .append(")");

        return executeUpdate(sql.toString(), values.toArray())
                .thenApply(affected -> affected > 0);
    }

    @Override
    public CompletableFuture<Boolean> upsert(String table, Map<String, Object> data, String primaryKey) {
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(table).append("` (");
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<String> updates = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            columns.add("`" + entry.getKey() + "`");
            placeholders.add("?");
            values.add(entry.getValue());
            if (!entry.getKey().equals(primaryKey)) {
                updates.add("`" + entry.getKey() + "` = VALUES(`" + entry.getKey() + "`)");
            }
        }

        sql.append(String.join(", ", columns))
                .append(") VALUES (")
                .append(String.join(", ", placeholders))
                .append(") ON DUPLICATE KEY UPDATE ");

        if (updates.isEmpty()) {
            sql.append("`").append(primaryKey).append("` = `").append(primaryKey).append("`");
        } else {
            sql.append(String.join(", ", updates));
        }

        return executeUpdate(sql.toString(), values.toArray())
                .thenApply(affected -> affected >= 0);
    }

    @Override
    public CompletableFuture<Integer> update(String table, Map<String, Object> data, Map<String, Object> conditions) {
        StringBuilder sql = new StringBuilder("UPDATE `").append(table).append("` SET ");
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            setClauses.add("`" + entry.getKey() + "` = ?");
            values.add(entry.getValue());
        }

        sql.append(String.join(", ", setClauses));

        if (conditions != null && !conditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> whereClauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                whereClauses.add("`" + entry.getKey() + "` = ?");
                values.add(entry.getValue());
            }
            sql.append(String.join(" AND ", whereClauses));
        }

        return executeUpdate(sql.toString(), values.toArray());
    }

    @Override
    public CompletableFuture<Integer> deleteWhere(String table, Map<String, Object> conditions) {
        StringBuilder sql = new StringBuilder("DELETE FROM `").append(table).append("`");
        List<Object> values = new ArrayList<>();

        if (conditions != null && !conditions.isEmpty()) {
            sql.append(" WHERE ");
            List<String> clauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                clauses.add("`" + entry.getKey() + "` = ?");
                values.add(entry.getValue());
            }
            sql.append(String.join(" AND ", clauses));
        }

        return executeUpdate(sql.toString(), values.toArray());
    }

    // ==================== Raw SQL ====================

    @Override
    public CompletableFuture<List<Map<String, Object>>> executeQuery(String sql, Object... params) {
        return executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(meta.getColumnLabel(i), rs.getObject(i));
                        }
                        results.add(row);
                    }
                    return results;
                }
            }
        });
    }

    @Override
    public CompletableFuture<Integer> executeUpdate(String sql, Object... params) {
        return executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                return stmt.executeUpdate();
            }
        });
    }

    // ==================== Transaction Support ====================

    @Override
    public <R> CompletableFuture<R> transaction(Function<Connection, R> action) {
        return executeAsync(conn -> {
            boolean autoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                R result = action.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw new SQLException("Transaction failed", e);
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        });
    }

    // ==================== Batch Operations ====================

    @Override
    public CompletableFuture<Boolean> batchInsert(String table, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        return executeAsync(conn -> {
            Map<String, Object> first = rows.get(0);
            List<String> columns = new ArrayList<>(first.keySet());

            StringBuilder sql = new StringBuilder("INSERT INTO `").append(table).append("` (");
            sql.append(columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(", ")));
            sql.append(") VALUES (");
            sql.append(columns.stream().map(c -> "?").collect(Collectors.joining(", ")));
            sql.append(")");

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                conn.setAutoCommit(false);

                for (Map<String, Object> row : rows) {
                    for (int i = 0; i < columns.size(); i++) {
                        stmt.setObject(i + 1, row.get(columns.get(i)));
                    }
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
                return true;
            }
        });
    }

    // ==================== Status ====================

    @Override
    public boolean isConnected() {
        return connected && dataSource != null && !dataSource.isClosed();
    }

    @Override
    public ProviderType getType() {
        return ProviderType.MYSQL;
    }

    @Override
    public CompletableFuture<Void> reconnect() {
        close();
        return initialize();
    }

    @Override
    public CompletableFuture<Long> ping() {
        long start = System.currentTimeMillis();
        return executeAsync(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return System.currentTimeMillis() - start;
            }
        }).exceptionally(e -> -1L);
    }

    @Override
    public void close() {
        connected = false;
        createdTables.clear();
        try {
            if (executor != null) {
                executor.shutdown();
                executor = null;
            }
            if (dataSource != null) {
                dataSource.close();
                dataSource = null;
            }
            log.info("MySQL connection closed");
        } catch (Exception e) {
            log.error("Error closing MySQL connection: {}", e.getMessage());
        }
    }
}
