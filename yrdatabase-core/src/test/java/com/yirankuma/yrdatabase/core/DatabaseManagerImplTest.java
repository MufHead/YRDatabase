package com.yirankuma.yrdatabase.core;

import com.yirankuma.yrdatabase.api.CacheStrategy;
import com.yirankuma.yrdatabase.api.DatabaseStatus;
import com.yirankuma.yrdatabase.api.config.DatabaseConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DatabaseManagerImpl with SQLite.
 * These tests use an in-memory SQLite database for isolation.
 *
 * @author YiranKuma
 */
@DisplayName("DatabaseManagerImpl Tests")
class DatabaseManagerImplTest {

    @TempDir
    Path tempDir;

    private DatabaseManagerImpl databaseManager;
    private DatabaseConfig config;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        config = new DatabaseConfig();
        
        // Disable Redis for unit tests
        config.getCache().setEnabled(false);
        
        // Use SQLite in temp directory
        config.getPersist().setEnabled(true);
        config.getPersist().setType("sqlite");
        
        File sqliteFile = tempDir.resolve("test.db").toFile();
        config.getPersist().getSqlite().setFile(sqliteFile.getAbsolutePath());
        
        databaseManager = new DatabaseManagerImpl(config);
        databaseManager.initialize().get();
    }

    @AfterEach
    void tearDown() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    @Nested
    @DisplayName("Connection Status")
    class ConnectionStatus {

        @Test
        @DisplayName("Should be connected after initialization")
        void shouldBeConnectedAfterInitialization() {
            assertTrue(databaseManager.isConnected());
        }

        @Test
        @DisplayName("Should have persist provider available")
        void shouldHavePersistProvider() {
            assertTrue(databaseManager.getPersistProvider().isPresent());
        }

        @Test
        @DisplayName("Should not have cache provider when disabled")
        void shouldNotHaveCacheProvider() {
            assertFalse(databaseManager.getCacheProvider().isPresent());
        }

        @Test
        @DisplayName("Should return valid status")
        void shouldReturnValidStatus() {
            DatabaseStatus status = databaseManager.getStatus();
            
            assertNotNull(status);
            assertTrue(status.isConnected());
            assertNotNull(status.getPersistStatus());
            assertEquals("sqlite", status.getPersistStatus().getType());
        }
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("Should set and get data")
        void shouldSetAndGetData() throws ExecutionException, InterruptedException {
            Map<String, Object> data = new HashMap<>();
            data.put("name", "TestPlayer");
            data.put("score", 100);

            // Set data
            Boolean setResult = databaseManager.set("test_table", "key1", data, 
                CacheStrategy.PERSIST_ONLY).get();
            assertTrue(setResult);

            // Get data
            Optional<Map<String, Object>> result = databaseManager.get("test_table", "key1").get();
            assertTrue(result.isPresent());
            assertEquals("TestPlayer", result.get().get("name"));
        }

        @Test
        @DisplayName("Should return empty for non-existent key")
        void shouldReturnEmptyForNonExistentKey() throws ExecutionException, InterruptedException {
            Optional<Map<String, Object>> result = databaseManager.get("test_table", "nonexistent").get();
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should check if key exists")
        void shouldCheckIfKeyExists() throws ExecutionException, InterruptedException {
            Map<String, Object> data = new HashMap<>();
            data.put("value", "test");

            databaseManager.set("test_table", "exists_key", data, CacheStrategy.PERSIST_ONLY).get();

            assertTrue(databaseManager.exists("test_table", "exists_key").get());
            assertFalse(databaseManager.exists("test_table", "not_exists").get());
        }

        @Test
        @DisplayName("Should delete data")
        void shouldDeleteData() throws ExecutionException, InterruptedException {
            Map<String, Object> data = new HashMap<>();
            data.put("value", "to_delete");

            databaseManager.set("test_table", "delete_key", data, CacheStrategy.PERSIST_ONLY).get();
            assertTrue(databaseManager.exists("test_table", "delete_key").get());

            Boolean deleteResult = databaseManager.delete("test_table", "delete_key").get();
            assertTrue(deleteResult);

            assertFalse(databaseManager.exists("test_table", "delete_key").get());
        }
    }

    @Nested
    @DisplayName("Table Management")
    class TableManagement {

        @Test
        @DisplayName("Should ensure table exists")
        void shouldEnsureTableExists() throws ExecutionException, InterruptedException {
            Map<String, String> schema = new HashMap<>();
            schema.put("id", "VARCHAR(64) PRIMARY KEY");
            schema.put("name", "VARCHAR(255)");
            schema.put("score", "INT DEFAULT 0");

            Boolean result = databaseManager.ensureTable("players", schema).get();
            assertTrue(result);

            // Second call should also succeed (table already exists)
            Boolean result2 = databaseManager.ensureTable("players", schema).get();
            assertTrue(result2);
        }
    }

    @Nested
    @DisplayName("Cache Strategies")
    class CacheStrategies {

        @Test
        @DisplayName("PERSIST_ONLY should write directly to database")
        void persistOnlyShouldWriteDirectly() throws ExecutionException, InterruptedException {
            Map<String, Object> data = new HashMap<>();
            data.put("value", "persist_only_test");

            Boolean result = databaseManager.set("strategy_test", "po_key", data, 
                CacheStrategy.PERSIST_ONLY).get();
            assertTrue(result);

            // Should be retrievable
            Optional<Map<String, Object>> retrieved = databaseManager.get("strategy_test", "po_key").get();
            assertTrue(retrieved.isPresent());
        }

        @Test
        @DisplayName("Default strategy should work")
        void defaultStrategyShouldWork() throws ExecutionException, InterruptedException {
            Map<String, Object> data = new HashMap<>();
            data.put("value", "default_test");

            // Use default set (without strategy)
            Boolean result = databaseManager.set("strategy_test", "default_key", data).get();
            assertTrue(result);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle null data gracefully")
        void shouldHandleNullData() {
            assertThrows(NullPointerException.class, () -> {
                databaseManager.set("test", "key", null).get();
            });
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("Should close gracefully")
        void shouldCloseGracefully() {
            databaseManager.close();
            
            // After close, operations should fail gracefully
            CompletableFuture<Optional<Map<String, Object>>> result = 
                databaseManager.get("test", "key");
            
            // Should complete (possibly with empty result)
            assertDoesNotThrow(() -> result.get());
        }
    }
}
