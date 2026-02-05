package com.yirankuma.yrdatabase.api.metrics;

/**
 * Metrics collector interface for database performance monitoring.
 * 
 * <p>Design: High Cohesion - focused on metric collection operations</p>
 * <p>Design: Low Coupling - interface only, no implementation dependencies</p>
 *
 * @author YiranKuma
 */
public interface MetricsCollector {

    /**
     * Record a database operation timing.
     *
     * @param operation Operation name (e.g., "get", "set", "query")
     * @param provider  Provider name (e.g., "redis", "mysql", "sqlite")
     * @param durationMs Operation duration in milliseconds
     * @param success   Whether the operation was successful
     */
    void recordOperation(String operation, String provider, long durationMs, boolean success);

    /**
     * Record a cache hit.
     *
     * @param table Table name
     */
    void recordCacheHit(String table);

    /**
     * Record a cache miss.
     *
     * @param table Table name
     */
    void recordCacheMiss(String table);

    /**
     * Record active connections count.
     *
     * @param provider Provider name
     * @param count    Connection count
     */
    void recordActiveConnections(String provider, int count);

    /**
     * Get current metrics snapshot.
     *
     * @return Metrics snapshot
     */
    MetricsSnapshot getSnapshot();

    /**
     * Reset all metrics.
     */
    void reset();
}
