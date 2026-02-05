package com.yirankuma.yrdatabase.core.metrics;

import com.yirankuma.yrdatabase.api.metrics.MetricsSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DefaultMetricsCollector.
 *
 * @author YiranKuma
 */
@DisplayName("DefaultMetricsCollector Tests")
class DefaultMetricsCollectorTest {

    private DefaultMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new DefaultMetricsCollector();
    }

    @Nested
    @DisplayName("Operation Recording")
    class OperationRecording {

        @Test
        @DisplayName("Should record successful operation")
        void shouldRecordSuccessfulOperation() {
            collector.recordOperation("get", "redis", 10, true);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(1, snapshot.getTotalOperations());
            assertEquals(1, snapshot.getSuccessfulOperations());
            assertEquals(0, snapshot.getFailedOperations());
        }

        @Test
        @DisplayName("Should record failed operation")
        void shouldRecordFailedOperation() {
            collector.recordOperation("get", "redis", 10, false);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(1, snapshot.getTotalOperations());
            assertEquals(0, snapshot.getSuccessfulOperations());
            assertEquals(1, snapshot.getFailedOperations());
        }

        @Test
        @DisplayName("Should calculate average latency")
        void shouldCalculateAverageLatency() {
            collector.recordOperation("get", "redis", 10, true);
            collector.recordOperation("get", "redis", 20, true);
            collector.recordOperation("get", "redis", 30, true);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(20.0, snapshot.getAverageLatencyMs(), 0.01);
        }

        @Test
        @DisplayName("Should track max latency")
        void shouldTrackMaxLatency() {
            collector.recordOperation("get", "redis", 10, true);
            collector.recordOperation("get", "redis", 50, true);
            collector.recordOperation("get", "redis", 20, true);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(50, snapshot.getMaxLatencyMs());
        }

        @Test
        @DisplayName("Should track min latency")
        void shouldTrackMinLatency() {
            collector.recordOperation("get", "redis", 50, true);
            collector.recordOperation("get", "redis", 10, true);
            collector.recordOperation("get", "redis", 20, true);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(10, snapshot.getMinLatencyMs());
        }
    }

    @Nested
    @DisplayName("Cache Statistics")
    class CacheStatistics {

        @Test
        @DisplayName("Should record cache hits")
        void shouldRecordCacheHits() {
            collector.recordCacheHit("players");
            collector.recordCacheHit("players");
            collector.recordCacheHit("items");
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(3, snapshot.getCacheHits());
        }

        @Test
        @DisplayName("Should record cache misses")
        void shouldRecordCacheMisses() {
            collector.recordCacheMiss("players");
            collector.recordCacheMiss("items");
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(2, snapshot.getCacheMisses());
        }

        @Test
        @DisplayName("Should calculate cache hit rate")
        void shouldCalculateCacheHitRate() {
            collector.recordCacheHit("table");
            collector.recordCacheHit("table");
            collector.recordCacheHit("table");
            collector.recordCacheMiss("table");
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(0.75, snapshot.getCacheHitRate(), 0.01);
        }

        @Test
        @DisplayName("Should handle zero cache requests")
        void shouldHandleZeroCacheRequests() {
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(0.0, snapshot.getCacheHitRate());
        }
    }

    @Nested
    @DisplayName("Provider Metrics")
    class ProviderMetrics {

        @Test
        @DisplayName("Should track per-provider metrics")
        void shouldTrackPerProviderMetrics() {
            collector.recordOperation("get", "redis", 5, true);
            collector.recordOperation("query", "mysql", 50, true);
            collector.recordOperation("get", "redis", 10, true);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertTrue(snapshot.getProviderMetrics().containsKey("redis"));
            assertTrue(snapshot.getProviderMetrics().containsKey("mysql"));
            
            assertEquals(2, snapshot.getProviderMetrics().get("redis").getOperationCount());
            assertEquals(1, snapshot.getProviderMetrics().get("mysql").getOperationCount());
        }

        @Test
        @DisplayName("Should track active connections")
        void shouldTrackActiveConnections() {
            collector.recordActiveConnections("redis", 5);
            collector.recordActiveConnections("mysql", 10);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(5, snapshot.getProviderMetrics().get("redis").getActiveConnections());
            assertEquals(10, snapshot.getProviderMetrics().get("mysql").getActiveConnections());
        }
    }

    @Nested
    @DisplayName("Operation Types")
    class OperationTypes {

        @Test
        @DisplayName("Should track per-operation metrics")
        void shouldTrackPerOperationMetrics() {
            collector.recordOperation("get", "redis", 10, true);
            collector.recordOperation("set", "redis", 15, true);
            collector.recordOperation("get", "redis", 8, true);
            collector.recordOperation("delete", "redis", 5, false);
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            
            assertEquals(2, snapshot.getOperationMetrics().get("get").getCount());
            assertEquals(1, snapshot.getOperationMetrics().get("set").getCount());
            assertEquals(1, snapshot.getOperationMetrics().get("delete").getCount());
            assertEquals(0, snapshot.getOperationMetrics().get("delete").getSuccessCount());
        }
    }

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("Should reset all counters")
        void shouldResetAllCounters() {
            collector.recordOperation("get", "redis", 10, true);
            collector.recordCacheHit("table");
            collector.recordActiveConnections("redis", 5);
            
            collector.reset();
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(0, snapshot.getTotalOperations());
            assertEquals(0, snapshot.getCacheHits());
            assertTrue(snapshot.getProviderMetrics().isEmpty());
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Should be thread-safe under concurrent access")
        void shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            collector.recordOperation("get", "redis", j % 100, j % 2 == 0);
                            collector.recordCacheHit("table" + threadId);
                            collector.recordCacheMiss("table" + threadId);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "All threads should complete");
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            MetricsSnapshot snapshot = collector.getSnapshot();
            assertEquals(threadCount * operationsPerThread, snapshot.getTotalOperations());
            assertEquals(threadCount * operationsPerThread, snapshot.getCacheHits());
            assertEquals(threadCount * operationsPerThread, snapshot.getCacheMisses());
        }
    }

    @Nested
    @DisplayName("Snapshot")
    class Snapshot {

        @Test
        @DisplayName("Should include timestamp in snapshot")
        void shouldIncludeTimestamp() {
            long before = System.currentTimeMillis();
            MetricsSnapshot snapshot = collector.getSnapshot();
            long after = System.currentTimeMillis();
            
            assertTrue(snapshot.getTimestamp() >= before);
            assertTrue(snapshot.getTimestamp() <= after);
        }

        @Test
        @DisplayName("Should calculate uptime")
        void shouldCalculateUptime() throws InterruptedException {
            Thread.sleep(100);
            MetricsSnapshot snapshot = collector.getSnapshot();
            
            assertTrue(snapshot.getUptimeMs() >= 100);
        }

        @Test
        @DisplayName("Should calculate operations per second")
        void shouldCalculateOperationsPerSecond() throws InterruptedException {
            // Record some operations
            for (int i = 0; i < 100; i++) {
                collector.recordOperation("get", "redis", 5, true);
            }
            
            Thread.sleep(100); // Wait a bit for rate calculation
            
            MetricsSnapshot snapshot = collector.getSnapshot();
            assertTrue(snapshot.getOperationsPerSecond() > 0);
        }
    }
}
