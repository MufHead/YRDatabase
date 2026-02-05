package com.yirankuma.yrdatabase.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseConfig.
 *
 * @author YiranKuma
 */
@DisplayName("DatabaseConfig Tests")
class DatabaseConfigTest {

    @Nested
    @DisplayName("Default Values")
    class DefaultValues {

        @Test
        @DisplayName("Should have default mode as standalone")
        void shouldHaveDefaultModeAsStandalone() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals("standalone", config.getMode());
        }

        @Test
        @DisplayName("Should have cache enabled by default")
        void shouldHaveCacheEnabledByDefault() {
            DatabaseConfig config = new DatabaseConfig();
            assertTrue(config.getCache().isEnabled());
        }

        @Test
        @DisplayName("Should have default Redis host as localhost")
        void shouldHaveDefaultRedisHost() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals("localhost", config.getCache().getHost());
        }

        @Test
        @DisplayName("Should have default Redis port as 6379")
        void shouldHaveDefaultRedisPort() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals(6379, config.getCache().getPort());
        }

        @Test
        @DisplayName("Should have persist enabled by default")
        void shouldHavePersistEnabledByDefault() {
            DatabaseConfig config = new DatabaseConfig();
            assertTrue(config.getPersist().isEnabled());
        }

        @Test
        @DisplayName("Should have default persist type as mysql")
        void shouldHaveDefaultPersistType() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals("mysql", config.getPersist().getType());
        }

        @Test
        @DisplayName("Should have default MySQL port as 3306")
        void shouldHaveDefaultMysqlPort() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals(3306, config.getPersist().getMysql().getPort());
        }
    }

    @Nested
    @DisplayName("Cache Config")
    class CacheConfigTest {

        @Test
        @DisplayName("Should allow setting custom Redis configuration")
        void shouldAllowCustomRedisConfig() {
            DatabaseConfig config = new DatabaseConfig();
            config.getCache().setHost("redis.example.com");
            config.getCache().setPort(6380);
            config.getCache().setPassword("secret");
            config.getCache().setDatabase(1);

            assertEquals("redis.example.com", config.getCache().getHost());
            assertEquals(6380, config.getCache().getPort());
            assertEquals("secret", config.getCache().getPassword());
            assertEquals(1, config.getCache().getDatabase());
        }

        @Test
        @DisplayName("Should have default pool settings")
        void shouldHaveDefaultPoolSettings() {
            DatabaseConfig config = new DatabaseConfig();
            DatabaseConfig.CacheConfig.PoolConfig pool = config.getCache().getPool();

            assertEquals(20, pool.getMaxTotal());
            assertEquals(10, pool.getMaxIdle());
            assertEquals(2, pool.getMinIdle());
        }
    }

    @Nested
    @DisplayName("Persist Config")
    class PersistConfigTest {

        @Test
        @DisplayName("Should allow setting MySQL configuration")
        void shouldAllowMysqlConfig() {
            DatabaseConfig config = new DatabaseConfig();
            config.getPersist().getMysql().setHost("mysql.example.com");
            config.getPersist().getMysql().setPort(3307);
            config.getPersist().getMysql().setDatabase("testdb");
            config.getPersist().getMysql().setUsername("user");
            config.getPersist().getMysql().setPassword("pass");

            assertEquals("mysql.example.com", config.getPersist().getMysql().getHost());
            assertEquals(3307, config.getPersist().getMysql().getPort());
            assertEquals("testdb", config.getPersist().getMysql().getDatabase());
            assertEquals("user", config.getPersist().getMysql().getUsername());
            assertEquals("pass", config.getPersist().getMysql().getPassword());
        }

        @Test
        @DisplayName("Should allow setting SQLite configuration")
        void shouldAllowSqliteConfig() {
            DatabaseConfig config = new DatabaseConfig();
            config.getPersist().getSqlite().setFile("/data/custom.db");

            assertEquals("/data/custom.db", config.getPersist().getSqlite().getFile());
        }

        @Test
        @DisplayName("Should have default MySQL pool settings")
        void shouldHaveDefaultMysqlPoolSettings() {
            DatabaseConfig config = new DatabaseConfig();
            DatabaseConfig.PersistConfig.MySQLConfig.PoolConfig pool = 
                config.getPersist().getMysql().getPool();

            assertEquals(10, pool.getMaxSize());
            assertEquals(2, pool.getMinIdle());
            assertEquals(30000, pool.getConnectionTimeout());
        }
    }

    @Nested
    @DisplayName("Caching Config")
    class CachingConfigTest {

        @Test
        @DisplayName("Should have default TTL values")
        void shouldHaveDefaultTtlValues() {
            DatabaseConfig config = new DatabaseConfig();
            
            assertEquals(3600, config.getCaching().getDefaultTTL());
            assertEquals(7200, config.getCaching().getPlayerDataTTL());
        }

        @Test
        @DisplayName("Should have auto refresh enabled by default")
        void shouldHaveAutoRefreshEnabled() {
            DatabaseConfig config = new DatabaseConfig();
            assertTrue(config.getCaching().isAutoRefresh());
        }
    }

    @Nested
    @DisplayName("Session Config")
    class SessionConfigTest {

        @Test
        @DisplayName("Should have default session timeout")
        void shouldHaveDefaultSessionTimeout() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals(300000, config.getSession().getTimeout());
        }

        @Test
        @DisplayName("Should have default heartbeat interval")
        void shouldHaveDefaultHeartbeatInterval() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals(10000, config.getSession().getHeartbeatInterval());
        }
    }

    @Nested
    @DisplayName("Advanced Config")
    class AdvancedConfigTest {

        @Test
        @DisplayName("Should have default executor size")
        void shouldHaveDefaultExecutorSize() {
            DatabaseConfig config = new DatabaseConfig();
            assertEquals(4, config.getAdvanced().getAsyncExecutorSize());
        }

        @Test
        @DisplayName("Should have metrics disabled by default")
        void shouldHaveMetricsDisabled() {
            DatabaseConfig config = new DatabaseConfig();
            assertFalse(config.getAdvanced().isEnableMetrics());
        }

        @Test
        @DisplayName("Should have debug mode disabled by default")
        void shouldHaveDebugModeDisabled() {
            DatabaseConfig config = new DatabaseConfig();
            assertFalse(config.getAdvanced().isDebugMode());
        }
    }
}
