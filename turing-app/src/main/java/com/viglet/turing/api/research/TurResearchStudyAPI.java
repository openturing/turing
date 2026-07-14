/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.research;

import java.time.Duration;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.research.TurResearchConceptFitService;
import com.viglet.turing.genai.research.TurResearchDriftService;
import com.viglet.turing.genai.research.TurResearchEvalBridgeService;
import com.viglet.turing.genai.research.TurResearchEventBus;
import com.viglet.turing.genai.research.TurResearchInsightsService;
import com.viglet.turing.genai.research.TurResearchRunEvent;
import com.viglet.turing.genai.research.TurResearchRunResultDto;
import com.viglet.turing.genai.research.TurResearchRunnerService;
import com.viglet.turing.genai.research.TurResearchStudyService;
import com.viglet.turing.genai.research.dto.TurResearchConceptFitDto;
import com.viglet.turing.genai.research.dto.TurResearchDriftDto;
import com.viglet.turing.genai.research.dto.TurResearchGraphDto;
import com.viglet.turing.genai.research.dto.TurResearchInterviewDto;
import com.viglet.turing.genai.research.dto.TurResearchReportDto;
import com.viglet.turing.genai.research.dto.TurResearchSaturationResultDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyDto;
import com.viglet.turing.genai.research.dto.TurResearchStudyPersonaRefDto;
import com.viglet.turing.persistence.dto.agent.TurEvalDatasetDto;
import com.viglet.turing.persistence.model.agent.TurEvalDataset;
import com.viglet.turing.tenant.TurTenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * REST API for Synthetic User Research studies (Block AW / §XLVI.2, T719/T721).
 * Admin console surface (secured by the global {@code authenticated()} rule like
 * the sibling persona APIs). Exposes study CRUD, the ordered audience roster, the
 * persisted interviews, and the cohort interview run — both blocking and as a
 * live SSE stream that fills the studio's interview feed as transcripts complete.
 *
 * <p>The study-scoped surfaces (interview feed, report, saturation) are
 * deliberately headless here; the {@code /bento/persona/research} studio (T730,
 * Phase 5) renders them.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/research-study")
@Tag(name = "Research Study", description = "Synthetic user research: cohort interviews & insights")
public class TurResearchStudyAPI {

    private final TurResearchStudyService service;
    private final TurResearchRunnerService runnerService;
    private final TurResearchInsightsService insightsService;
    private final TurResearchEvalBridgeService evalBridgeService;
    private final TurResearchConceptFitService conceptFitService;
    private final TurResearchDriftService driftService;
    private final TurResearchEventBus eventBus;
    private final TurTenantContext tenantContext;

    public TurResearchStudyAPI(TurResearchStudyService service,
            TurResearchRunnerService runnerService,
            TurResearchInsightsService insightsService,
            TurResearchEvalBridgeService evalBridgeService,
            TurResearchConceptFitService conceptFitService,
            TurResearchDriftService driftService,
            TurResearchEventBus eventBus,
            TurTenantContext tenantContext) {
        this.service = service;
        this.runnerService = runnerService;
        this.insightsService = insightsService;
        this.evalBridgeService = evalBridgeService;
        this.conceptFitService = conceptFitService;
        this.driftService = driftService;
        this.eventBus = eventBus;
        this.tenantContext = tenantContext;
    }

    // ---- study CRUD --------------------------------------------------------

    @Operation(summary = "List research studies")
    @GetMapping
    public List<TurResearchStudyDto> list() {
        return service.list();
    }

    @Operation(summary = "Show a study (with roster + interviews)")
    @GetMapping("/{id}")
    public TurResearchStudyDto get(@PathVariable String id) {
        return service.get(id).orElseThrow(() -> TurNotFoundException.of("Research study", id));
    }

    @Operation(summary = "Create a study")
    @PostMapping
    public TurResearchStudyDto create(@RequestBody TurResearchStudyDto dto) {
        return service.create(dto);
    }

    @Operation(summary = "Update a study")
    @PutMapping("/{id}")
    public TurResearchStudyDto update(@PathVariable String id,
            @RequestBody TurResearchStudyDto dto) {
        return service.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Research study", id));
    }

    @Operation(summary = "Delete a study")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        return service.delete(id);
    }

    // ---- audience roster ---------------------------------------------------

    @Operation(summary = "The study's ordered audience roster")
    @GetMapping("/{id}/personas")
    public List<TurResearchStudyPersonaRefDto> personas(@PathVariable String id) {
        return service.personas(id);
    }

    @Operation(summary = "Replace the study's ordered audience roster")
    @PutMapping("/{id}/personas")
    public List<TurResearchStudyPersonaRefDto> setPersonas(@PathVariable String id,
            @RequestBody List<String> personaIds) {
        return service.setPersonas(id, personaIds);
    }

    // ---- interviews --------------------------------------------------------

    @Operation(summary = "The persisted interview transcripts")
    @GetMapping("/{id}/interviews")
    public List<TurResearchInterviewDto> interviews(@PathVariable String id) {
        return service.interviews(id);
    }

    // ---- synthesis & sufficiency (Phase 3) ---------------------------------

    @Operation(summary = "Synthesized insights report — themes, verbatim quotes, "
            + "recommendations (cached, regenerable) with by-theme / by-persona lenses")
    @GetMapping("/{id}/insights")
    public TurResearchReportDto insights(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        return insightsService.report(id, regenerate);
    }

    @Operation(summary = "Deterministic saturation scoring (LLM-free) — when marginal "
            + "personas stop adding new themes")
    @GetMapping("/{id}/saturation")
    public TurResearchSaturationResultDto saturation(@PathVariable String id) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        return insightsService.saturation(id);
    }

    @Operation(summary = "Theme / affinity knowledge graph — persona↔theme edges "
            + "derived from the insights report (which cohorts cluster on which pain point)")
    @GetMapping("/{id}/graph")
    public TurResearchGraphDto graph(@PathVariable String id) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        return insightsService.graph(id);
    }

    // ---- studies -> eval bridge (Phase 4, T725) ----------------------------

    @Operation(summary = "Promote the study's completed interviews into a reusable eval "
            + "dataset (agent-QA harness); the studio deep-links the returned id into /bento/eval")
    @PostMapping("/{id}/promote-to-dataset")
    public TurEvalDatasetDto promoteToDataset(@PathVariable String id,
            @RequestParam(required = false) String name) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        TurEvalDataset dataset = evalBridgeService.promoteToDataset(id, name);
        return TurEvalDatasetDto.summary(dataset, dataset.getRows().size());
    }

    // ---- concept-test <-> Persona Match bridge (Phase 4, T727) -------------

    @Operation(summary = "Concept fit — score a CONCEPT_TEST study's concept against each "
            + "roster persona through the shared Block AT content-fit evaluator (reuses the "
            + "Match report surface)")
    @GetMapping("/{id}/concept-fit")
    public TurResearchConceptFitDto conceptFit(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean regenerate) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        return conceptFitService.conceptFit(id, regenerate);
    }

    // ---- continuous insight — drift (Phase 4, T729) ------------------------

    @Operation(summary = "Insight drift — the ordered series of deterministic sufficiency "
            + "snapshots captured after each run (theme coverage + saturation over time)")
    @GetMapping("/{id}/drift")
    public TurResearchDriftDto drift(@PathVariable String id) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        return driftService.drift(id);
    }

    // ---- run ---------------------------------------------------------------

    @Operation(summary = "Run the cohort interviews (blocking) and return the transcripts")
    @PostMapping("/{id}/run")
    public TurResearchRunResultDto run(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        return runnerService.run(id, force);
    }

    @Operation(summary = "Run the cohort interviews and stream progress over SSE (live feed)")
    @PostMapping(value = "/{id}/run/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<TurResearchRunEvent>> runStream(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force) {
        if (service.get(id).isEmpty()) {
            throw TurNotFoundException.of("Research study", id);
        }
        String tenant = tenantContext.getCurrentTenant();

        Flux<ServerSentEvent<TurResearchRunEvent>> data = eventBus.subscribe(id)
                .takeUntil(TurResearchStudyAPI::terminal)
                .map(event -> ServerSentEvent.builder(event).build());

        Flux<ServerSentEvent<TurResearchRunEvent>> run = Mono
                .fromRunnable(() -> tenantContext.runAs(tenant, () -> runnerService.run(id, force)))
                .subscribeOn(Schedulers.boundedElastic())
                .thenMany(Flux.empty());

        return Flux.merge(data, run, heartbeat())
                .takeUntil(sse -> sse.data() != null && terminal(sse.data()));
    }

    private static boolean terminal(TurResearchRunEvent event) {
        return event.type() == TurResearchRunEvent.Type.DONE
                || event.type() == TurResearchRunEvent.Type.ERROR;
    }

    /** 25s comment heartbeat so a long-running run keeps the SSE alive. */
    private static Flux<ServerSentEvent<TurResearchRunEvent>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(25))
                .map(tick -> ServerSentEvent.<TurResearchRunEvent>builder()
                        .comment("heartbeat").build());
    }
}
