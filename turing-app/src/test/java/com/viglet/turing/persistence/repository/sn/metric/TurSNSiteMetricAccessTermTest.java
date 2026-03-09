package com.viglet.turing.persistence.repository.sn.metric;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class TurSNSiteMetricAccessTermTest {

    @Test
    void shouldCreateUsingTermAndAccessDateConstructor() {
        Instant now = Instant.now();
        TurSNSiteMetricAccessTerm metricTerm = new TurSNSiteMetricAccessTerm("ai search", now);

        assertThat(metricTerm.getTerm()).isEqualTo("ai search");
        assertThat(metricTerm.getAcessDate()).isEqualTo(now);
        assertThat(metricTerm.getTotal()).isZero();
        assertThat(metricTerm.getNumFound()).isZero();
    }

    @Test
    void shouldRoundNumFoundInTermTotalConstructor() {
        TurSNSiteMetricAccessTerm metricTerm = new TurSNSiteMetricAccessTerm("ranking", 7L, 12.6);

        assertThat(metricTerm.getTerm()).isEqualTo("ranking");
        assertThat(metricTerm.getTotal()).isEqualTo(7L);
        assertThat(metricTerm.getNumFound()).isEqualTo(13.0);
    }
}
