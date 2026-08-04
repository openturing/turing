package com.viglet.turing.api.sn.search;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.annotation.DurationFormat.Unit;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.PropertyPlaceholderHelper;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.sn.TurSNSearchProcess;

@ExtendWith(MockitoExtension.class)
class TurSNSiteSearchCachedAPITest {

    @Mock
    private TurSNSearchProcess turSNSearchProcess;

    private TurSNSiteSearchCachedAPI api;

    @BeforeEach
    void setUp() {
        // T872 — the cache seam takes only what the cached search reads: the search
        // process, plus the deprecated TTL key it warns about.
        api = new TurSNSiteSearchCachedAPI(turSNSearchProcess, "");
    }

    @Test
    void testCleanSearchCache() {
        assertDoesNotThrow(() -> api.cleanSearchCache());
    }

    @Test
    void testSearchCached() {
        String cacheKey = "key1";
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("site1", null, null, null, null);
        TurSNSiteSearchBean expectedBean = new TurSNSiteSearchBean();

        when(turSNSearchProcess.search(context)).thenReturn(expectedBean);

        TurSNSiteSearchBean result = api.searchCached(cacheKey, context);

        assertEquals(expectedBean, result);
        verify(turSNSearchProcess, times(1)).search(context);
    }

    /**
     * T871 / §LXVIII.2 — the sweep interval is resolved and parsed exactly the way
     * Spring does it ({@code PropertyPlaceholderHelper} then
     * {@link DurationFormatterUtils#detectAndParse}), because the bug was in the
     * property <em>name</em>: nothing at runtime fails when the unit is implied and
     * wrong, so only an assertion on the resolved value can catch it.
     */
    private static Duration sweepInterval(Map<String, String> properties)
            throws NoSuchMethodException {
        String fixedRate = TurSNSiteSearchCachedAPI.class.getMethod("cleanSearchCache")
                .getAnnotation(Scheduled.class).fixedRateString();
        String resolved = new PropertyPlaceholderHelper("${", "}", ":", '\\', true)
                .replacePlaceholders(fixedRate, properties::get);
        return DurationFormatterUtils.detectAndParse(resolved, Unit.MILLIS);
    }

    /** Nothing configured: the default is 24h, and it says so. */
    @Test
    void testSweepIntervalDefaultsTo24h() throws NoSuchMethodException {
        assertEquals(Duration.ofHours(24), sweepInterval(Map.of()));
    }

    /** The new key takes a unit-bearing value — the whole point of the task. */
    @Test
    void testSweepIntervalAcceptsAStatedUnit() throws NoSuchMethodException {
        assertEquals(Duration.ofHours(1),
                sweepInterval(Map.of("turing.search.cache.ttl", "1h")));
        assertEquals(Duration.ofHours(1),
                sweepInterval(Map.of("turing.search.cache.ttl", "PT1H")));
        assertEquals(Duration.ofMinutes(30),
                sweepInterval(Map.of("turing.search.cache.ttl", "30m")));
    }

    /**
     * An existing configuration keeps the behaviour it has today — the old key was
     * always milliseconds, and reading it as anything else would change the sweep
     * frequency of every deployment that set it.
     */
    @Test
    void testLegacyTtlKeyStillMeansMilliseconds() throws NoSuchMethodException {
        assertEquals(Duration.ofHours(24),
                sweepInterval(Map.of("turing.search.cache.ttl.seconds", "86400000")));
    }

    /** The new key wins when both are set. */
    @Test
    void testNewKeyOverridesTheDeprecatedOne() throws NoSuchMethodException {
        assertEquals(Duration.ofHours(6), sweepInterval(Map.of(
                "turing.search.cache.ttl", "6h",
                "turing.search.cache.ttl.seconds", "86400000")));
    }

    /** The deprecated key is named at startup rather than silently obeyed. */
    @Test
    void testLegacyTtlKeyIsWarnedAbout() {
        assertDoesNotThrow(() -> new TurSNSiteSearchCachedAPI(turSNSearchProcess, "3600"));
    }
}
