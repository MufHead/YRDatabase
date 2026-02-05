package com.yirankuma.yrdatabase.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheStrategy enum.
 *
 * @author YiranKuma
 */
@DisplayName("CacheStrategy Tests")
class CacheStrategyTest {

    @Test
    @DisplayName("Should have four strategies")
    void shouldHaveFourStrategies() {
        CacheStrategy[] strategies = CacheStrategy.values();
        assertEquals(4, strategies.length, "Should have exactly 4 cache strategies");
    }

    @Test
    @DisplayName("CACHE_ONLY should be defined")
    void cacheOnlyShouldBeDefined() {
        CacheStrategy strategy = CacheStrategy.valueOf("CACHE_ONLY");
        assertNotNull(strategy);
        assertEquals(CacheStrategy.CACHE_ONLY, strategy);
    }

    @Test
    @DisplayName("PERSIST_ONLY should be defined")
    void persistOnlyShouldBeDefined() {
        CacheStrategy strategy = CacheStrategy.valueOf("PERSIST_ONLY");
        assertNotNull(strategy);
        assertEquals(CacheStrategy.PERSIST_ONLY, strategy);
    }

    @Test
    @DisplayName("CACHE_FIRST should be defined")
    void cacheFirstShouldBeDefined() {
        CacheStrategy strategy = CacheStrategy.valueOf("CACHE_FIRST");
        assertNotNull(strategy);
        assertEquals(CacheStrategy.CACHE_FIRST, strategy);
    }

    @Test
    @DisplayName("WRITE_THROUGH should be defined")
    void writeThroughShouldBeDefined() {
        CacheStrategy strategy = CacheStrategy.valueOf("WRITE_THROUGH");
        assertNotNull(strategy);
        assertEquals(CacheStrategy.WRITE_THROUGH, strategy);
    }
}
