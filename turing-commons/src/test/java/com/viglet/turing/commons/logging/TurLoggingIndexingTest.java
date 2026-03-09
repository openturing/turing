package com.viglet.turing.commons.logging;

import com.viglet.turing.commons.indexing.TurIndexingStatus;
import com.viglet.turing.commons.indexing.TurLoggingStatus;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TurLoggingIndexingTest {

    @Test
    void shouldBuildAndExposeIndexingLogFields() {
        Date now = new Date();
        TurLoggingIndexing log = TurLoggingIndexing.builder()
                .status(TurIndexingStatus.INDEXED)
                .source("cms")
                .contentId("doc-1")
                .url("http://localhost/doc-1")
                .sites(List.of("site-a", "site-b"))
                .environment("prod")
                .locale(Locale.US)
                .transactionId("tx-1")
                .checksum("abc")
                .resultStatus(TurLoggingStatus.SUCCESS)
                .details("ok")
                .date(now)
                .build();

        assertThat(log.getStatus()).isEqualTo(TurIndexingStatus.INDEXED);
        assertThat(log.getSource()).isEqualTo("cms");
        assertThat(log.getContentId()).isEqualTo("doc-1");
        assertThat(log.getUrl()).contains("doc-1");
        assertThat(log.getSites()).containsExactly("site-a", "site-b");
        assertThat(log.getEnvironment()).isEqualTo("prod");
        assertThat(log.getLocale()).isEqualTo(Locale.US);
        assertThat(log.getTransactionId()).isEqualTo("tx-1");
        assertThat(log.getChecksum()).isEqualTo("abc");
        assertThat(log.getResultStatus()).isEqualTo(TurLoggingStatus.SUCCESS);
        assertThat(log.getDetails()).isEqualTo("ok");
        assertThat(log.getDate()).isEqualTo(now);
    }
}
