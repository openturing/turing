package com.viglet.turing.commons.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TurIndexingStatusTest {

    @Test
    void shouldContainExpectedStatuses() {
        assertThat(TurIndexingStatus.values())
                .contains(TurIndexingStatus.PREPARE_INDEX)
                .contains(TurIndexingStatus.PREPARE_UNCHANGED)
                .contains(TurIndexingStatus.PREPARE_REINDEX)
                .contains(TurIndexingStatus.PREPARE_FORCED_REINDEX)
                .contains(TurIndexingStatus.RECEIVED_AND_SENT_TO_TURING)
                .contains(TurIndexingStatus.SENT_TO_QUEUE)
                .contains(TurIndexingStatus.NOT_PROCESSED)
                .contains(TurIndexingStatus.INDEXED)
                .contains(TurIndexingStatus.DEINDEXED)
                .contains(TurIndexingStatus.IGNORED)
                .contains(TurIndexingStatus.FINISHED)
                .contains(TurIndexingStatus.RECEIVED_FROM_QUEUE);
    }
}
