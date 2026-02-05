package com.yirankuma.yrdatabase.api.config;

import lombok.Data;

/**
 * Database configuration.
 *
 * @author YiranKuma
 */
@Data
public class DatabaseConfig {

    /**
     * Operating mode: standalone or cluster.
     */
    private String mode = "standalone";

    /**
     * Cache layer configuration.
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * Persistence layer configuration.
     */
    private PersistConfig persist = new PersistConfig();

    /**
     * Caching strategy configuration.
     */
    private CachingConfig caching = new CachingConfig();

    /**
     * Session management configuration.
     */
    private SessionConfig session = new SessionConfig();

    /**
     * Advanced options.
     */
    private AdvancedConfig advanced = new AdvancedConfig();

    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private String type = "redis";
        private String host = "localhost";
        private int port = 6379;
        private String password = "";
        private int database = 0;
        private int timeout = 5000;
        private PoolConfig pool = new PoolConfig();

        @Data
        public static class PoolConfig {
            private int maxTotal = 20;
            private int maxIdle = 10;
            private int minIdle = 2;
        }
    }

    @Data
    public static class PersistConfig {
        private boolean enabled = true;
        private String type = "mysql";
        private MySQLConfig mysql = new MySQLConfig();
        private SQLiteConfig sqlite = new SQLiteConfig();

        @Data
        public static class MySQLConfig {
            private String host = "localhost";
            private int port = 3306;
            private String database = "yrdatabase";
            private String username = "root";
            private String password = "";
            private String timezone = "Asia/Shanghai";
            private PoolConfig pool = new PoolConfig();

            @Data
            public static class PoolConfig {
                private int maxSize = 10;
                private int minIdle = 2;
                private long connectionTimeout = 30000;
                private long idleTimeout = 600000;
                private long maxLifetime = 1800000;
            }
        }

        @Data
        public static class SQLiteConfig {
            private String file = "data/yrdatabase.db";
        }
    }

    @Data
    public static class CachingConfig {
        private long defaultTTL = 3600;
        private long playerDataTTL = 7200;
        private boolean autoRefresh = true;
        private long refreshThreshold = 300;
    }

    @Data
    public static class SessionConfig {
        private long timeout = 300000;
        private long heartbeatInterval = 10000;
        private long messageExpiry = 30000;
    }

    @Data
    public static class AdvancedConfig {
        private int asyncExecutorSize = 4;
        private boolean enableMetrics = false;
        private boolean debugMode = false;
    }
}
