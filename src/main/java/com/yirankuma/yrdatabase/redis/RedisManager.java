package com.yirankuma.yrdatabase.redis;

import com.yirankuma.yrdatabase.config.DatabaseConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RedisManager {
    
    private RedisClient redisClient;
    private GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool;
    private DatabaseConfig.Redis config;
    private boolean connected = false;
    
    public RedisManager(DatabaseConfig.Redis config) {
        this.config = config;
    }
    
    public void initialize() {
        if (!config.isEnabled()) {
            return;
        }
        
        try {
            RedisURI.Builder uriBuilder = RedisURI.builder()
                    .withHost(config.getHost())
                    .withPort(config.getPort())
                    .withDatabase(config.getDatabase())
                    .withTimeout(Duration.ofMillis(config.getTimeout()));
            
            if (!config.getPassword().isEmpty()) {
                uriBuilder.withPassword(config.getPassword().toCharArray());
            }
            
            redisClient = RedisClient.create(uriBuilder.build());
            
            // 创建连接池
            GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(config.getMaxConnections());
            poolConfig.setMaxIdle(config.getMaxConnections());
            poolConfig.setMinIdle(1);
            
            connectionPool = ConnectionPoolSupport.createGenericObjectPool(
                    () -> redisClient.connect(), poolConfig);
            
            // 测试连接
            try (StatefulRedisConnection<String, String> connection = connectionPool.borrowObject()) {
                connection.sync().ping();
                connected = true;
            }
            
        } catch (Exception e) {
            connected = false;
            throw new RuntimeException("Failed to initialize Redis connection", e);
        }
    }
    
    public void shutdown() {
        if (connectionPool != null) {
            connectionPool.close();
        }
        if (redisClient != null) {
            redisClient.shutdown();
        }
        connected = false;
    }
    
    public boolean isConnected() {
        return connected && config.isEnabled();
    }
    
    // ========== 基础操作 ==========
    
    public CompletableFuture<String> get(String key) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return executeAsync(commands -> commands.get(key).toCompletableFuture());
    }
    
    public CompletableFuture<Boolean> set(String key, String value, long expireSeconds) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return executeAsync(commands -> {
            if (expireSeconds > 0) {
                return commands.setex(key, expireSeconds, value).toCompletableFuture().thenApply(result -> "OK".equals(result));
            } else {
                return commands.set(key, value).toCompletableFuture().thenApply(result -> "OK".equals(result));
            }
        });
    }
    
    public CompletableFuture<Boolean> exists(String key) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return executeAsync(commands -> commands.exists(key).toCompletableFuture().thenApply(count -> count > 0));
    }
    
    public CompletableFuture<String> hget(String key, String field) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return executeAsync(commands -> commands.hget(key, field).toCompletableFuture());
    }
    
    public CompletableFuture<Boolean> hset(String key, String field, String value) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return executeAsync(commands -> commands.hset(key, field, value).toCompletableFuture().thenApply(result -> result != null));
    }
    
    public CompletableFuture<Map<String, String>> hgetAll(String key) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return executeAsync(commands -> commands.hgetall(key).toCompletableFuture());
    }
    
    public CompletableFuture<Boolean> hdel(String key, String field) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return executeAsync(commands -> commands.hdel(key, field).toCompletableFuture().thenApply(count -> count > 0));
    }
    
    public CompletableFuture<List<String>> keys(String pattern) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        return executeAsync(commands -> commands.keys(pattern).toCompletableFuture());
    }
    
    public CompletableFuture<Boolean> delete(String key) {
        if (!isConnected()) {
            return CompletableFuture.completedFuture(false);
        }
        
        return executeAsync(commands -> commands.del(key).toCompletableFuture().thenApply(count -> count > 0));
    }
    
    // ========== 辅助方法 ==========
    
    @FunctionalInterface
    private interface AsyncCommand<T> {
        CompletableFuture<T> execute(RedisAsyncCommands<String, String> commands);
    }
    
    private <T> CompletableFuture<T> executeAsync(AsyncCommand<T> command) {
        try {
            StatefulRedisConnection<String, String> connection = connectionPool.borrowObject();
            RedisAsyncCommands<String, String> commands = connection.async();
            
            return command.execute(commands).whenComplete((result, throwable) -> {
                try {
                    connectionPool.returnObject(connection);
                } catch (Exception e) {
                    // Log error
                }
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}