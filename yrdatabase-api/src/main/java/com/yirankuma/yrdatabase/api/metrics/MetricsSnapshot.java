package com.yirankuma.yrdatabase.api.metrics;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Snapshot of current metrics.
 *
 * @author YiranKuma
 */
@Data
@Builder
public class MetricsSnapshot {

    /**
     * Total operations count.
     */
    private final long totalOperations;

    /**
     * Successful operations count.
     */
    private final long successfulOperations;

    /**
     * Failed operations count.
     */
    private final long failedOperations;

    /**
     * Average operation latency in milliseconds.
     */
    private final double averageLatencyMs;

    /**
     * Maximum operation latency in milliseconds.
     */
    private final long maxLatencyMs;

    /**
     * Minimum operation latency in milliseconds.
     */
    private final long minLatencyMs;

    /**
     * 95th percentile latency in milliseconds.
     */
    private final long p95LatencyMs;

    /**
     * 99th percentile latency in milliseconds.
     */
    private final long p99LatencyMs;

    /**
     * Cache hit count.
     */
    private final long cacheHits;

    /**
     * Cache miss count.
     */
    private final long cacheMisses;

    /**
     * Cache hit rate (0.0 - 1.0).
     */
    private final double cacheHitRate;

    /**
     * Operations per second.
     */
    private final double operationsPerSecond;

    /**
     * Per-operation metrics.
     */
    private final Map<String, OperationMetrics> operationMetrics;

    /**
     * Per-provider metrics.
     */
    private final Map<String, ProviderMetrics> providerMetrics;

    /**
     * Snapshot timestamp.
     */
    private final long timestamp;

    /**
     * Uptime in milliseconds.
     */
    private final long uptimeMs;

    @Data
    @Builder
    public static class OperationMetrics {
        private final String operation;
        private final long count;
        private final long successCount;
        private final long failureCount;
        private final double averageLatencyMs;
        private final long maxLatencyMs;
    }

    @Data
    @Builder
    public static class ProviderMetrics {
        private final String provider;
        private final long operationCount;
        private final double averageLatencyMs;
        private final int activeConnections;
        private final boolean connected;
    }
}
