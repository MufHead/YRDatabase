package com.yirankuma.yrdatabase.core.provider.redis;

import com.yirankuma.yrdatabase.api.config.DatabaseConfig;
import com.yirankuma.yrdatabase.api.provider.CacheProvider;
import com.yirankuma.yrdatabase.api.provider.ProviderType;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.support.ConnectionPoolSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Redis cache provider implementation using Lettuce.
 *
 * @author YiranKuma
 */
@Slf4j
public class RedisProvider implements CacheProvider {

    private final DatabaseConfig.CacheConfig config;
    private ClientResources clientResources;
    private RedisClient redisClient;
    private GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final Map<String, Consumer<String>> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean connected = false;

    public RedisProvider(DatabaseConfig.CacheConfig config) {
        this.config = config;
    }

    /**
     * Initialize the Redis connection.
     *
     * @return Completion future
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                clientResources = DefaultClientResources.builder()
                        .ioThreadPoolSize(4)
                        .computationThreadPoolSize(4)
                        .build();

                RedisURI.Builder uriBuilder = RedisURI.builder()
                        .withHost(config.getHost())
                        .withPort(config.getPort())
                        .withDatabase(config.getDatabase())
                        .withTimeout(Duration.ofMillis(config.getTimeout()));

                if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                    uriBuilder.withPassword(config.getPassword().toCharArray());
                }

                redisClient = RedisClient.create(clientResources, uriBuilder.build());

                // Create connection pool
                GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig =
                        new GenericObjectPoolConfig<>();
                poolConfig.setMaxTotal(config.getPool().getMaxTotal());
                poolConfig.setMaxIdle(config.getPool().getMaxIdle());
                poolConfig.setMinIdle(config.getPool().getMinIdle());
                poolConfig.setTestOnBorrow(true);
                poolConfig.setTestWhileIdle(true);

                connectionPool = ConnectionPoolSupport.createGenericObjectPool(
                        redisClient::connect, poolConfig);

                // Create pub/sub connection
                pubSubConnection = redisClient.connectPubSub();
                pubSubConnection.addListener(new PubSubListener());

                connected = true;
                log.info("Redis connected successfully to {}:{}", config.getHost(), config.getPort());
            } catch (Exception e) {
                log.error("Failed to connect to Redis: {}", e.getMessage());
                connected = false;
                throw new RuntimeException("Failed to connect to Redis", e);
            }
        });
    }

    private <T> CompletableFuture<T> executeAsync(AsyncCommand<T> command) {
        if (!connected) {
            return CompletableFuture.failedFuture(new IllegalStateException("Redis not connected"));
        }

        return CompletableFuture.supplyAsync(() -> {
            StatefulRedisConnection<String, String> connection = null;
            try {
                connection = connectionPool.borrowObject();
                RedisAsyncCommands<String, String> commands = connection.async();
                return command.execute(commands).toCompletableFuture().join();
            } catch (Exception e) {
                log.error("Redis command failed: {}", e.getMessage());
                throw new RuntimeException(e);
            } finally {
                if (connection != null) {
                    connectionPool.returnObject(connection);
                }
            }
        });
    }

    @FunctionalInterface
    private interface AsyncCommand<T> {
        io.lettuce.core.RedisFuture<T> execute(RedisAsyncCommands<String, String> commands);
    }

    // ==================== Basic Operations ====================

    @Override
    public CompletableFuture<Optional<String>> get(String key) {
        return executeAsync(cmd -> cmd.get(key)).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Boolean> set(String key, String value) {
        return executeAsync(cmd -> cmd.set(key, value)).thenApply("OK"::equals);
    }

    @Override
    public CompletableFuture<Boolean> setEx(String key, String value, Duration ttl) {
        return executeAsync(cmd -> cmd.setex(key, ttl.getSeconds(), value)).thenApply("OK"::equals);
    }

    @Override
    public CompletableFuture<Boolean> delete(String key) {
        return executeAsync(cmd -> cmd.del(key)).thenApply(count -> count > 0);
    }

    @Override
    public CompletableFuture<Boolean> exists(String key) {
        return executeAsync(cmd -> cmd.exists(key)).thenApply(count -> count > 0);
    }

    // ==================== Batch Operations ====================

    @Override
    public CompletableFuture<Map<String, String>> mget(List<String> keys) {
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
        String[] keyArray = keys.toArray(new String[0]);
        return executeAsync(cmd -> cmd.mget(keyArray)).thenApply(values -> {
            Map<String, String> result = new HashMap<>();
            for (int i = 0; i < keys.size(); i++) {
                if (values.get(i).hasValue()) {
                    result.put(keys.get(i), values.get(i).getValue());
                }
            }
            return result;
        });
    }

    @Override
    public CompletableFuture<Boolean> mset(Map<String, String> entries) {
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        return executeAsync(cmd -> cmd.mset(entries)).thenApply("OK"::equals);
    }

    // ==================== Hash Operations ====================

    @Override
    public CompletableFuture<Optional<String>> hget(String key, String field) {
        return executeAsync(cmd -> cmd.hget(key, field)).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletableFuture<Boolean> hset(String key, String field, String value) {
        return executeAsync(cmd -> cmd.hset(key, field, value)).thenApply(result -> true);
    }

    @Override
    public CompletableFuture<Map<String, String>> hgetAll(String key) {
        return executeAsync(cmd -> cmd.hgetall(key));
    }

    @Override
    public CompletableFuture<Boolean> hdel(String key, String... fields) {
        return executeAsync(cmd -> cmd.hdel(key, fields)).thenApply(count -> count > 0);
    }

    @Override
    public CompletableFuture<Boolean> hmset(String key, Map<String, String> fields) {
        return executeAsync(cmd -> cmd.hmset(key, fields)).thenApply("OK"::equals);
    }

    // ==================== Cache-specific Operations ====================

    @Override
    public CompletableFuture<Boolean> expire(String key, Duration ttl) {
        return executeAsync(cmd -> cmd.expire(key, ttl.getSeconds()));
    }

    @Override
    public CompletableFuture<Long> ttl(String key) {
        return executeAsync(cmd -> cmd.ttl(key));
    }

    @Override
    public CompletableFuture<List<String>> keys(String pattern) {
        return executeAsync(cmd -> cmd.keys(pattern));
    }

    // ==================== Pub/Sub ====================

    @Override
    public void subscribe(String channel, Consumer<String> handler) {
        subscriptions.put(channel, handler);
        if (pubSubConnection != null) {
            RedisPubSubAsyncCommands<String, String> async = pubSubConnection.async();
            async.subscribe(channel);
        }
    }

    @Override
    public void unsubscribe(String channel) {
        subscriptions.remove(channel);
        if (pubSubConnection != null) {
            RedisPubSubAsyncCommands<String, String> async = pubSubConnection.async();
            async.unsubscribe(channel);
        }
    }

    @Override
    public CompletableFuture<Long> publish(String channel, String message) {
        return executeAsync(cmd -> cmd.publish(channel, message));
    }

    // ==================== Atomic Operations ====================

    @Override
    public CompletableFuture<Long> incr(String key) {
        return executeAsync(cmd -> cmd.incr(key));
    }

    @Override
    public CompletableFuture<Long> incrBy(String key, long amount) {
        return executeAsync(cmd -> cmd.incrby(key, amount));
    }

    @Override
    public CompletableFuture<Long> decr(String key) {
        return executeAsync(cmd -> cmd.decr(key));
    }

    @Override
    public CompletableFuture<Boolean> setNx(String key, String value) {
        return executeAsync(cmd -> cmd.setnx(key, value));
    }

    @Override
    public CompletableFuture<Boolean> setNxEx(String key, String value, Duration ttl) {
        return executeAsync(cmd -> cmd.set(key, value,
                io.lettuce.core.SetArgs.Builder.nx().ex(ttl.getSeconds())))
                .thenApply("OK"::equals);
    }

    // ==================== Status ====================

    @Override
    public boolean isConnected() {
        return connected && connectionPool != null;
    }

    @Override
    public ProviderType getType() {
        return ProviderType.REDIS;
    }

    @Override
    public CompletableFuture<Void> reconnect() {
        close();
        return initialize();
    }

    @Override
    public CompletableFuture<Long> ping() {
        long start = System.currentTimeMillis();
        return executeAsync(cmd -> cmd.ping())
                .thenApply(pong -> System.currentTimeMillis() - start)
                .exceptionally(e -> -1L);
    }

    @Override
    public void close() {
        connected = false;
        try {
            if (pubSubConnection != null) {
                pubSubConnection.close();
                pubSubConnection = null;
            }
            if (connectionPool != null) {
                connectionPool.close();
                connectionPool = null;
            }
            if (redisClient != null) {
                redisClient.shutdown();
                redisClient = null;
            }
            if (clientResources != null) {
                clientResources.shutdown();
                clientResources = null;
            }
            log.info("Redis connection closed");
        } catch (Exception e) {
            log.error("Error closing Redis connection: {}", e.getMessage());
        }
    }

    /**
     * Internal pub/sub listener.
     */
    private class PubSubListener implements RedisPubSubListener<String, String> {
        @Override
        public void message(String channel, String message) {
            Consumer<String> handler = subscriptions.get(channel);
            if (handler != null) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    log.error("Error handling pub/sub message on channel {}: {}", channel, e.getMessage());
                }
            }
        }

        @Override
        public void message(String pattern, String channel, String message) {
            // Pattern subscription not used
        }

        @Override
        public void subscribed(String channel, long count) {
            log.debug("Subscribed to channel: {}", channel);
        }

        @Override
        public void psubscribed(String pattern, long count) {
            // Pattern subscription not used
        }

        @Override
        public void unsubscribed(String channel, long count) {
            log.debug("Unsubscribed from channel: {}", channel);
        }

        @Override
        public void punsubscribed(String pattern, long count) {
            // Pattern subscription not used
        }
    }
}
