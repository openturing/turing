package com.viglet.turing.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for TurLlmCacheService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLlmCacheServiceTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;

    private TurLlmCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new TurLlmCacheService(globalSettingsService);
    }

    // --- get ---

    @Test
    void getShouldReturnNullWhenCacheDisabled() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(false);

        assertNull(cacheService.get("site-1"));
    }

    @Test
    void getShouldReturnNullWhenNoEntryExists() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);

        assertNull(cacheService.get("nonexistent"));
    }

    @ParameterizedTest(name = "ttl={0}ms content=[{1}]")
    @CsvSource({
            "3600000, cached content",
            "60000,   fresh content",
            "3600000, ''"
    })
    void getShouldReturnCachedContentWhenValid(long ttlMs, String content) {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(ttlMs);

        cacheService.put("site-1", content);

        assertEquals(content, cacheService.get("site-1"));
    }

    @Test
    void getShouldReturnNullWhenEntryExpired() throws InterruptedException {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        // Use 1ms TTL for immediate expiration
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(1L);

        cacheService.put("site-1", "old content");

        // Small sleep to ensure TTL expires
        Thread.sleep(10);

        assertNull(cacheService.get("site-1"));
    }

    @Test
    void getShouldReturnContentWhenTtlNotYetExpired() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(60_000L);

        cacheService.put("site-1", "fresh content");

        assertEquals("fresh content", cacheService.get("site-1"));
    }

    // --- put ---

    @Test
    void putShouldNotStoreWhenCacheDisabled() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(false);

        cacheService.put("site-1", "content");

        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        assertNull(cacheService.get("site-1"));
    }

    @Test
    void putShouldOverwriteExistingEntry() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(3_600_000L);

        cacheService.put("site-1", "first");
        cacheService.put("site-1", "second");

        assertEquals("second", cacheService.get("site-1"));
    }

    @Test
    void putShouldStoreMultipleSites() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(3_600_000L);

        cacheService.put("site-1", "content-1");
        cacheService.put("site-2", "content-2");

        assertEquals("content-1", cacheService.get("site-1"));
        assertEquals("content-2", cacheService.get("site-2"));
    }

    // --- evict ---

    @Test
    void evictShouldRemoveSpecificEntry() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(3_600_000L);

        cacheService.put("site-1", "content-1");
        cacheService.put("site-2", "content-2");

        cacheService.evict("site-1");

        assertNull(cacheService.get("site-1"));
        assertEquals("content-2", cacheService.get("site-2"));
    }

    @Test
    void evictShouldNotThrowForNonexistentKey() {
        assertDoesNotThrow(() -> cacheService.evict("nonexistent"));
    }

    // --- evictAll ---

    @Test
    void evictAllShouldClearAllEntries() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);

        cacheService.put("site-1", "content-1");
        cacheService.put("site-2", "content-2");
        cacheService.put("site-3", "content-3");

        cacheService.evictAll();

        assertNull(cacheService.get("site-1"));
        assertNull(cacheService.get("site-2"));
        assertNull(cacheService.get("site-3"));
    }

    @Test
    void evictAllShouldNotThrowWhenEmpty() {
        assertDoesNotThrow(() -> cacheService.evictAll());
    }

    // --- edge cases ---

    @Test
    void getShouldHandleNullSiteId() {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);

        assertThrows(NullPointerException.class, () -> cacheService.get(null));
    }

    @Test
    void expiredEntryShouldBeRemovedFromCache() throws InterruptedException {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(1L);

        cacheService.put("site-1", "content");

        Thread.sleep(10);

        // First get removes the expired entry
        assertNull(cacheService.get("site-1"));
        // Second get should also return null (entry was removed)
        assertNull(cacheService.get("site-1"));
    }

    @Test
    void zeroTtlShouldExpireImmediately() throws InterruptedException {
        when(globalSettingsService.isLlmCacheEnabled()).thenReturn(true);
        when(globalSettingsService.getLlmCacheTtlMs()).thenReturn(0L);

        cacheService.put("site-1", "content");

        Thread.sleep(5);

        assertNull(cacheService.get("site-1"));
    }
}
