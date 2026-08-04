package com.viglet.turing.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

/**
 * T872 / §LXVIII.3 — the MongoDB log purge, now a bean of its own rather than a
 * passenger on the search cache seam.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLoggingPurgeServiceTest {

    private static TurLoggingPurgeService service(boolean enabled, int purgeDays) {
        return new TurLoggingPurgeService(enabled, "mongodb://localhost:27017", "turingLog",
                "server", "indexing", "aem", purgeDays);
    }

    /**
     * With {@code turing.mongodb.enabled} false the job returns before it ever
     * builds a client, so no Mongo is needed to assert it.
     */
    @Test
    void purgeIsANoOpWhenMongoIsDisabled() {
        assertDoesNotThrow(() -> service(false, 30).purgeMongoDBLogs());
    }

    @Test
    void purgeDateIsTheConfiguredNumberOfDaysAgo() {
        Instant before = Instant.now();
        Date purgeDate = service(false, 30).getPurgeDate();

        Duration age = Duration.between(purgeDate.toInstant(), before);
        assertTrue(age.compareTo(Duration.ofDays(30).minusMinutes(1)) >= 0
                && age.compareTo(Duration.ofDays(30).plusMinutes(1)) <= 0,
                "expected a cut-off ~30 days old, got " + age);
    }

    @Test
    void purgeDateOfZeroDaysIsNow() {
        Date purgeDate = service(false, 0).getPurgeDate();

        assertTrue(Duration.between(purgeDate.toInstant(), Instant.now())
                .compareTo(Duration.ofMinutes(1)) < 0);
    }
}
