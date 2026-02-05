package com.yirankuma.yrdatabase.core.metrics;

import com.yirankuma.yrdatabase.api.metrics.MetricsCollector;
import com.yirankuma.yrdatabase.api.metrics.MetricsSnapshot;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Default implementation of MetricsCollector.
 * 
 * <p>Thread-safe implementation using atomic operations and concurrent data structures.</p>
 * <p>Uses LongAdder for high-throughput counters and fixed-size arrays for histograms.</p>
 *
 * @author YiranKuma
 */
@Slf4j
public class DefaultMetricsCollector implements MetricsCollector {

    private final long startTime;
    
    // Global counters
    private final LongAdder totalOperations = new LongAdder();
    private final LongAdder successfulOperations = new LongAdder();
    private final LongAdder failedOperations = new LongAdder();
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    
    // Latency tracking
    private final LongAdder totalLatency = new LongAdder();
    private final AtomicLong maxLatency = new AtomicLong(0);
    private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
    
    // Histogram for percentile calculations (bucket size: 1ms, max: 10000ms)
    private static final int HISTOGRAM_SIZE = 10001;
    private final LongAdder[] latencyHistogram = new LongAdder[HISTOGRAM_SIZE];
    
    // Per-operation metrics
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    
    // Per-provider metrics
    private final Map<String, ProviderMetrics> providerMetrics = new ConcurrentHashMap<>();
    
    // Per-table cache metrics
    private final Map<String, TableCacheMetrics> tableCacheMetrics = new ConcurrentHashMap<>();

    public DefaultMetricsCollector() {
        this.startTime = System.currentTimeMillis();
        
        // Initialize histogram buckets
        for (int i = 0; i < HISTOGRAM_SIZE; i++) {
            latencyHistogram[i] = new LongAdder();
        }
    }

    @Override
    public void recordOperation(String operation, String provider, long durationMs, boolean success) {
        totalOperations.increment();
        totalLatency.add(durationMs);
        
        if (success) {
            successfulOperations.increment();
        } else {
            failedOperations.increment();
        }
        
        // Update min/max latency
        updateMinMax(durationMs);
        
        // Update histogram
        int bucket = (int) Math.min(durationMs, HISTOGRAM_SIZE - 1);
        latencyHistogram[bucket].increment();
        
        // Update per-operation metrics
        operationMetrics.computeIfAbsent(operation, k -> new OperationMetrics())
                .record(durationMs, success);
        
        // Update per-provider metrics
        providerMetrics.computeIfAbsent(provider, k -> new ProviderMetrics())
                .record(durationMs);
    }

    @Override
    public void recordCacheHit(String table) {
        cacheHits.increment();
        tableCacheMetrics.computeIfAbsent(table, k -> new TableCacheMetrics())
                .recordHit();
    }

    @Override
    public void recordCacheMiss(String table) {
        cacheMisses.increment();
        tableCacheMetrics.computeIfAbsent(table, k -> new TableCacheMetrics())
                .recordMiss();
    }

    @Override
    public void recordActiveConnections(String provider, int count) {
        providerMetrics.computeIfAbsent(provider, k -> new ProviderMetrics())
                .setActiveConnections(count);
    }

    @Override
    public MetricsSnapshot getSnapshot() {
        long total = totalOperations.sum();
        long successful = successfulOperations.sum();
        long failed = failedOperations.sum();
        long hits = cacheHits.sum();
        long misses = cacheMisses.sum();
        long totalLat = totalLatency.sum();
        long now = System.currentTimeMillis();
        long uptime = now - startTime;
        
        double avgLatency = total > 0 ? (double) totalLat / total : 0;
        double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) : 0;
        double opsPerSec = uptime > 0 ? (double) total / (uptime / 1000.0) : 0;
        
        // Calculate percentiles
        long p95 = calculatePercentile(95);
        long p99 = calculatePercentile(99);
        
        // Build operation metrics map
        Map<String, MetricsSnapshot.OperationMetrics> opMetrics = new HashMap<>();
        operationMetrics.forEach((op, m) -> {
            opMetrics.put(op, MetricsSnapshot.OperationMetrics.builder()
                    .operation(op)
                    .count(m.count.sum())
                    .successCount(m.successCount.sum())
                    .failureCount(m.failureCount.sum())
                    .averageLatencyMs(m.getAverageLatency())
                    .maxLatencyMs(m.maxLatency.get())
                    .build());
        });
        
        // Build provider metrics map
        Map<String, MetricsSnapshot.ProviderMetrics> provMetrics = new HashMap<>();
        providerMetrics.forEach((prov, m) -> {
            provMetrics.put(prov, MetricsSnapshot.ProviderMetrics.builder()
                    .provider(prov)
                    .operationCount(m.operationCount.sum())
                    .averageLatencyMs(m.getAverageLatency())
                    .activeConnections(m.activeConnections)
                    .connected(m.activeConnections > 0)
                    .build());
        });
        
        return MetricsSnapshot.builder()
                .totalOperations(total)
                .successfulOperations(successful)
                .failedOperations(failed)
                .averageLatencyMs(avgLatency)
                .maxLatencyMs(maxLatency.get())
                .minLatencyMs(minLatency.get() == Long.MAX_VALUE ? 0 : minLatency.get())
                .p95LatencyMs(p95)
                .p99LatencyMs(p99)
                .cacheHits(hits)
                .cacheMisses(misses)
                .cacheHitRate(hitRate)
                .operationsPerSecond(opsPerSec)
                .operationMetrics(opMetrics)
                .providerMetrics(provMetrics)
                .timestamp(now)
                .uptimeMs(uptime)
                .build();
    }

    @Override
    public void reset() {
        totalOperations.reset();
        successfulOperations.reset();
        failedOperations.reset();
        cacheHits.reset();
        cacheMisses.reset();
        totalLatency.reset();
        maxLatency.set(0);
        minLatency.set(Long.MAX_VALUE);
        
        for (LongAdder bucket : latencyHistogram) {
            bucket.reset();
        }
        
        operationMetrics.clear();
        providerMetrics.clear();
        tableCacheMetrics.clear();
    }

    // ==================== Private Helpers ====================

    private void updateMinMax(long latency) {
        // Update max
        long currentMax;
        do {
            currentMax = maxLatency.get();
            if (latency <= currentMax) break;
        } while (!maxLatency.compareAndSet(currentMax, latency));
        
        // Update min
        long currentMin;
        do {
            currentMin = minLatency.get();
            if (latency >= currentMin) break;
        } while (!minLatency.compareAndSet(currentMin, latency));
    }

    private long calculatePercentile(int percentile) {
        long total = totalOperations.sum();
        if (total == 0) return 0;
        
        long threshold = (long) (total * percentile / 100.0);
        long count = 0;
        
        for (int i = 0; i < HISTOGRAM_SIZE; i++) {
            count += latencyHistogram[i].sum();
            if (count >= threshold) {
                return i;
            }
        }
        
        return HISTOGRAM_SIZE - 1;
    }

    // ==================== Internal Metrics Classes ====================

    private static class OperationMetrics {
        final LongAdder count = new LongAdder();
        final LongAdder successCount = new LongAdder();
        final LongAdder failureCount = new LongAdder();
        final LongAdder totalLatency = new LongAdder();
        final AtomicLong maxLatency = new AtomicLong(0);

        void record(long latency, boolean success) {
            count.increment();
            totalLatency.add(latency);
            if (success) {
                successCount.increment();
            } else {
                failureCount.increment();
            }
            
            long currentMax;
            do {
                currentMax = maxLatency.get();
                if (latency <= currentMax) break;
            } while (!maxLatency.compareAndSet(currentMax, latency));
        }

        double getAverageLatency() {
            long c = count.sum();
            return c > 0 ? (double) totalLatency.sum() / c : 0;
        }
    }

    private static class ProviderMetrics {
        final LongAdder operationCount = new LongAdder();
        final LongAdder totalLatency = new LongAdder();
        volatile int activeConnections = 0;

        void record(long latency) {
            operationCount.increment();
            totalLatency.add(latency);
        }

        void setActiveConnections(int count) {
            this.activeConnections = count;
        }

        double getAverageLatency() {
            long c = operationCount.sum();
            return c > 0 ? (double) totalLatency.sum() / c : 0;
        }
    }

    private static class TableCacheMetrics {
        final LongAdder hits = new LongAdder();
        final LongAdder misses = new LongAdder();

        void recordHit() {
            hits.increment();
        }

        void recordMiss() {
            misses.increment();
        }
    }
}
