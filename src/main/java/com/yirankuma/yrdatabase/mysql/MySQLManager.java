package com.yirankuma.yrdatabase.mysql;

import com.yirankuma.yrdatabase.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MySQLManager {
    
    private HikariDataSource dataSource;
    private DatabaseConfig.MySQL config;
    private boolean connected = false;
    private final Executor executor = Executors.newCachedThreadPool();
    
    public MySQLManager(DatabaseConfig.MySQL config) {
        this.config = config;
    }
    
    public void initialize() {
        if (!config.isEnabled()) {
            return;
        }
        
        try {
            // 显式注册 MySQL 驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            HikariConfig hikariConfig = new HikariConfig();
            
            // 使用配置文件中的时区设置
            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=%s&characterEncoding=utf8",
                    config.getHost(), config.getPort(), config.getDatabase(), config.getTimezone());
            
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
            hikariConfig.setMinimumIdle(config.getMinIdle());
            hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
            hikariConfig.setIdleTimeout(config.getIdleTimeout());
            hikariConfig.setMaxLifetime(config.getMaxLifetime());
            
            // 连接池配置
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            dataSource = new HikariDataSource(hikariConfig);
            
            // 测试连接
            try (Connection connection = dataSource.getConnection()) {
                connected = connection.isValid(5);
            }
            
        } catch (Exception e) {
            connected = false;
            throw new RuntimeException("Failed to initialize MySQL connection", e);
        }
    }
    
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        connected = false;
    }
    
    public boolean isConnected() {
        return connected && config.isEnabled();
    }
    
    // ========== 表操作 ==========
    
    public CompletableFuture<Boolean> createTable(String tableName, Map<String, String> columns) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS `" + tableName + "` (");
            
            List<String> columnDefs = new ArrayList<>();
            for (Map.Entry<String, String> entry : columns.entrySet()) {
                columnDefs.add("`" + entry.getKey() + "` " + entry.getValue());
            }
            
            sql.append(String.join(", ", columnDefs));
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                
                stmt.executeUpdate(sql.toString());
                return true;
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    public CompletableFuture<Boolean> dropTable(String tableName) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return executeUpdate("DROP TABLE IF EXISTS `" + tableName + "`");
    }
    
    public CompletableFuture<Boolean> tableExists(String tableName) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, config.getDatabase());
                stmt.setString(2, tableName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    // ========== 数据操作 ==========
    
    public CompletableFuture<Boolean> insertIntoTable(String tableName, Map<String, Object> data) {
        if (!isConnected() || data.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            List<String> columns = new ArrayList<>(data.keySet());
            String sql = "INSERT INTO `" + tableName + "` (`" + 
                    String.join("`, `", columns) + "`) VALUES (" + 
                    String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                int index = 1;
                for (String column : columns) {
                    stmt.setObject(index++, data.get(column));
                }
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    public CompletableFuture<List<Map<String, Object>>> selectFromTable(String tableName, Map<String, Object> where) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("SELECT * FROM `" + tableName + "`");
            
            if (where != null && !where.isEmpty()) {
                sql.append(" WHERE ");
                List<String> conditions = new ArrayList<>();
                for (String column : where.keySet()) {
                    conditions.add("`" + column + "` = ?");
                }
                sql.append(String.join(" AND ", conditions));
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                
                if (where != null && !where.isEmpty()) {
                    int index = 1;
                    for (Object value : where.values()) {
                        stmt.setObject(index++, value);
                    }
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return resultSetToList(rs);
                }
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    public CompletableFuture<Boolean> updateTable(String tableName, Map<String, Object> data, Map<String, Object> where) {
        if (!isConnected() || data.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("UPDATE `" + tableName + "` SET ");
            
            List<String> setClauses = new ArrayList<>();
            for (String column : data.keySet()) {
                setClauses.add("`" + column + "` = ?");
            }
            sql.append(String.join(", ", setClauses));
            
            if (where != null && !where.isEmpty()) {
                sql.append(" WHERE ");
                List<String> conditions = new ArrayList<>();
                for (String column : where.keySet()) {
                    conditions.add("`" + column + "` = ?");
                }
                sql.append(String.join(" AND ", conditions));
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                
                int index = 1;
                for (Object value : data.values()) {
                    stmt.setObject(index++, value);
                }
                
                if (where != null && !where.isEmpty()) {
                    for (Object value : where.values()) {
                        stmt.setObject(index++, value);
                    }
                }
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    public CompletableFuture<Boolean> deleteFromTable(String tableName, Map<String, Object> where) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sql = new StringBuilder("DELETE FROM `" + tableName + "`");
            
            if (where != null && !where.isEmpty()) {
                sql.append(" WHERE ");
                List<String> conditions = new ArrayList<>();
                for (String column : where.keySet()) {
                    conditions.add("`" + column + "` = ?");
                }
                sql.append(String.join(" AND ", conditions));
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                
                if (where != null && !where.isEmpty()) {
                    int index = 1;
                    for (Object value : where.values()) {
                        stmt.setObject(index++, value);
                    }
                }
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    // ========== 自定义SQL ==========
    
    public CompletableFuture<List<Map<String, Object>>> executeQuery(String sql, Object... params) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return resultSetToList(rs);
                }
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    public CompletableFuture<Boolean> executeUpdate(String sql, Object... params) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    // ========== 辅助方法 ==========
    
    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            result.add(row);
        }
        
        return result;
    }
    
    public DatabaseConfig.MySQL getConfig() {
        return config;
    }
}