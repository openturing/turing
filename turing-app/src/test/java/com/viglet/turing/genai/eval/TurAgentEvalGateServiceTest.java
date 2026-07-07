/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.agent.TurAgentEvalCaseResultDto;
import com.viglet.turing.persistence.dto.agent.TurAgentEvalGateDto;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalReport;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalReportRepository;
import com.viglet.turing.persistence.repository.agent.TurAgentEvalSetRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * T287 / §XV.3 — unit coverage for the pre-publish gate status logic. Runs
 * without a Spring context or an LLM: it only exercises the report → gate
 * mapping (NOT_CONFIGURED / NEVER_RUN / GREEN / RED / REGRESSED) and the
 * blocking decision.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurAgentEvalGateServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AGENT_ID = "agent-1";

    @Mock
    private TurAgentEvalSetRepository evalSetRepository;
    @Mock
    private TurAgentEvalReportRepository reportRepository;

    @InjectMocks
    private TurAgentEvalGateService gateService;

    @Test
    void notConfigured_whenNoEnabledSet() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID)).thenReturn(List.of());

        TurAgentEvalGateDto gate = gateService.gate(AGENT_ID);

        assertThat(gate.status()).isEqualTo("NOT_CONFIGURED");
        assertThat(gate.findings()).isEmpty();
        assertThat(gate.blocking()).isFalse();
    }

    @Test
    void neverRun_whenSetExistsButNoReport() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(false)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.empty());

        TurAgentEvalGateDto gate = gateService.gate(AGENT_ID);

        assertThat(gate.status()).isEqualTo("NEVER_RUN");
        assertThat(gate.findings()).hasSize(1);
        assertThat(gate.findings().get(0).code()).isEqualTo("eval_never_run");
    }

    @Test
    void green_whenLatestPassed() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(false)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report(true, false, caseResult("c1", true))));

        TurAgentEvalGateDto gate = gateService.gate(AGENT_ID);

        assertThat(gate.status()).isEqualTo("GREEN");
        assertThat(gate.findings()).isEmpty();
    }

    @Test
    void red_whenFailingWithoutRegression() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(false)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report(false, false, caseResult("c1", false))));

        TurAgentEvalGateDto gate = gateService.gate(AGENT_ID);

        assertThat(gate.status()).isEqualTo("RED");
        assertThat(gate.findings()).anyMatch(f -> f.code().equals("eval_case_failed"));
        assertThat(gate.findings()).allMatch(f -> f.severity().equals("WARNING"));
    }

    @Test
    void regressed_whenFailingAgainstBaseline() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(true)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report(false, true, caseResult("c1", false))));

        TurAgentEvalGateDto gate = gateService.gate(AGENT_ID);

        assertThat(gate.status()).isEqualTo("REGRESSED");
        assertThat(gate.blocking()).isTrue();
        assertThat(gate.findings()).anyMatch(f -> f.code().equals("eval_regression")
                && f.severity().equals("ERROR"));
    }

    @Test
    void shouldBlockPublish_onlyWhenBlockingAndRed() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(true)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report(false, true, caseResult("c1", false))));

        assertThat(gateService.shouldBlockPublish(AGENT_ID)).isTrue();
    }

    @Test
    void shouldNotBlockPublish_whenGreen() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(true)));
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report(true, false, caseResult("c1", true))));

        assertThat(gateService.shouldBlockPublish(AGENT_ID)).isFalse();
    }

    @Test
    void pendingReview_warnsWhenNotBlocking() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(false)));
        TurAgentEvalReport report = report(false, false, caseResult("c1", false));
        report.setPendingReview(true);
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report));

        TurAgentEvalGateDto gate = gateService.gate(AGENT_ID);

        assertThat(gate.status()).isEqualTo("PENDING_REVIEW");
        assertThat(gate.findings()).hasSize(1);
        assertThat(gate.findings().get(0).code()).isEqualTo("eval_pending_review");
        assertThat(gate.findings().get(0).severity()).isEqualTo("WARNING");
    }

    @Test
    void pendingReview_blocksWhenBlockingSet() {
        when(evalSetRepository.findByTurAIAgent_IdOrderByNameAsc(AGENT_ID))
                .thenReturn(List.of(setWithCase(true)));
        TurAgentEvalReport report = report(false, false, caseResult("c1", false));
        report.setPendingReview(true);
        when(reportRepository.findFirstByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(Optional.of(report));

        assertThat(gateService.gate(AGENT_ID).findings().get(0).severity()).isEqualTo("ERROR");
        assertThat(gateService.shouldBlockPublish(AGENT_ID)).isTrue();
    }

    @Test
    void history_mapsReportsNewestFirstWithBreakdown() {
        TurAgentEvalReport older = report(false, false, caseResult("c1", false));
        older.setId("report-old");
        older.setCreatedAt(LocalDateTime.parse("2026-06-14T12:00:00"));
        TurAgentEvalReport newer = report(true, false, caseResult("c1", true));
        newer.setId("report-new");
        when(reportRepository.findByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(List.of(newer, older));

        var history = gateService.history(AGENT_ID);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).reportId()).isEqualTo("report-new");
        assertThat(history.get(0).passed()).isTrue();
        assertThat(history.get(0).results()).hasSize(1);
        assertThat(history.get(1).reportId()).isEqualTo("report-old");
        assertThat(history.get(1).passed()).isFalse();
    }

    @Test
    void history_emptyWhenNoReports() {
        when(reportRepository.findByTurAIAgent_IdOrderByCreatedAtDesc(AGENT_ID))
                .thenReturn(List.of());

        assertThat(gateService.history(AGENT_ID)).isEmpty();
    }

    // ─────────────────────────── Fixtures ───────────────────────────

    private static TurAgentEvalSet setWithCase(boolean blocking) {
        TurAgentEvalSet set = new TurAgentEvalSet();
        set.setEnabled(1);
        set.setBlocking(blocking ? 1 : 0);
        TurAgentEvalCase c = new TurAgentEvalCase();
        c.setName("c1");
        set.getCases().add(c);
        return set;
    }

    private static TurAgentEvalReport report(boolean passed, boolean regressed,
            TurAgentEvalCaseResultDto... results) {
        TurAgentEvalReport report = new TurAgentEvalReport();
        report.setId("report-1");
        report.setCreatedAt(LocalDateTime.parse("2026-06-15T12:00:00"));
        report.setPassed(passed);
        report.setRegressed(regressed);
        report.setCaseCount(results.length);
        report.setPassedCount((int) java.util.Arrays.stream(results)
                .filter(TurAgentEvalCaseResultDto::passed).count());
        report.setResultsJson(MAPPER.writeValueAsString(List.of(results)));
        return report;
    }

    private static TurAgentEvalCaseResultDto caseResult(String id, boolean passed) {
        return new TurAgentEvalCaseResultDto(id, id, passed, passed ? 1d : 0d,
                "ANY", "ABANDONED", null, List.of(), "na", null, null);
    }
}
