/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.console;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.viglet.turing.api.sn.bean.TurSNSiteMonitoringStatusBean;
import com.viglet.turing.domain.sn.SnSiteIndexInvalidatedEvent;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.exchange.sn.TurSNSiteExport;
import com.viglet.turing.persistence.dto.sn.TurSNSiteDto;
import com.viglet.turing.persistence.dto.sn.TurSNSiteListDto;
import com.viglet.turing.persistence.mapper.sn.TurSNSiteMapper;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNQueue;
import com.viglet.turing.sn.template.TurSNTemplate;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/sn")
@Tag(name = "Semantic Navigation Site", description = "Semantic Navigation Site API")
@ComponentScan("com.viglet.turing")
@RequiredArgsConstructor
public class TurSNSiteAPI {

    // --- S1192: extracted duplicated literals ---
    private static final String ERROR = "error";
    private static final String TASK_ID = "taskId";

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteGenAiRepository turSNSiteGenAiRepository;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSNSiteExport turSNSiteExport;
    private final TurSNSiteContentExchangeService contentExchangeService;
    private final TurSNTemplate turSNTemplate;
    private final TurSNQueue turSNQueue;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteMapper turSNSiteMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Operation(summary = "Semantic Navigation Site List")
    @GetMapping
    @Transactional(readOnly = true)
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public List<TurSNSiteListDto> turSNSiteList() {
        // T262 / §XIV.2.6 — the legacy createdBy pseudo-tenant filter is retired;
        // tenant isolation is now enforced automatically by the Hibernate
        // @TenantId discriminator on TurSNSite (T260).
        List<TurSNSite> sites = turSNSiteRepository.findAllForListing();
        return turSNSiteMapper.toListDtoList(sites);
    }

    @Operation(summary = "Semantic Navigation Site structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public TurSNSiteDto turSNSiteStructure() {
        TurSNSite turSNSite = new TurSNSite();
        turSNSite.setFacetSort(TurSNSiteFacetSortEnum.COUNT);
        turSNSite.setFacetType(TurSNSiteFacetFieldEnum.AND);
        turSNSite.setTurSEInstance(new TurSEInstance());
        turSNSite.setTurSNSiteGenAi(new TurSNSiteGenAi());
        return turSNSiteMapper.toDto(turSNSite);
    }

    @Operation(summary = "Show a Semantic Navigation Site")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public TurSNSiteDto turSNSiteGet(@PathVariable String id) {
        return turSNSiteMapper.toDto(this.turSNSiteRepository.findByIdWithGenAi(id).orElse(new TurSNSite()));
    }

    @Operation(summary = "Update a Semantic Navigation Site")
    @PutMapping("/{id}")
    @Transactional
    @Secured({"ROLE_ADMIN", "SN_EDIT"})
    public ResponseEntity<Object> turSNSiteUpdate(@PathVariable String id, @RequestBody TurSNSiteDto turSNSiteDto) {
        Optional<TurSNSite> duplicate = turSNSiteRepository.findByNameIgnoreCase(turSNSiteDto.getName())
                .filter(site -> !site.getId().equals(id));
        if (duplicate.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(ERROR, "A site with this name already exists."));
        }
        TurSNSite turSNSite = turSNSiteMapper.toEntity(turSNSiteDto);
        return ResponseEntity.ok(this.turSNSiteRepository.findByIdWithGenAi(id).map(turSNSiteEdit -> {
            turSNSiteEdit.setName(turSNSite.getName());
            turSNSiteEdit.setDescription(turSNSite.getDescription());
            turSNSiteEdit.setIcon(turSNSite.getIcon());
            turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());
            turSNSiteEdit.setThesaurus(turSNSite.getThesaurus());
            turSNSiteEdit.setFacet(turSNSite.getFacet());
            turSNSiteEdit.setFacetType(turSNSite.getFacetType());
            turSNSiteEdit.setFacetItemType(turSNSite.getFacetItemType());
            turSNSiteEdit.setFacetSort(turSNSite.getFacetSort());
            turSNSiteEdit.setHl(turSNSite.getHl());
            turSNSiteEdit.setHlPost(turSNSite.getHlPost());
            turSNSiteEdit.setHlPre(turSNSite.getHlPre());
            turSNSiteEdit.setItemsPerFacet(turSNSite.getItemsPerFacet());
            turSNSiteEdit.setSpellCheck(turSNSite.getSpellCheck());
            turSNSiteEdit.setSpellCheckFixes(turSNSite.getSpellCheckFixes());
            turSNSiteEdit.setMlt(turSNSite.getMlt());
            turSNSiteEdit.setRowsPerPage(turSNSite.getRowsPerPage());
            turSNSiteEdit.setSpotlightWithResults(turSNSite.getSpotlightWithResults());
            turSNSiteEdit.setWildcardNoResults(turSNSite.getWildcardNoResults());
            turSNSiteEdit.setWildcardAlways(turSNSite.getWildcardAlways());
            turSNSiteEdit.setExactMatchField(turSNSite.getExactMatchField());
            turSNSiteEdit.setDefaultField(turSNSite.getDefaultField());
            turSNSiteEdit.setDefaultTitleField(turSNSite.getDefaultTitleField());
            turSNSiteEdit.setDefaultTextField(turSNSite.getDefaultTextField());
            turSNSiteEdit.setDefaultDescriptionField(turSNSite.getDefaultDescriptionField());
            turSNSiteEdit.setDefaultDateField(turSNSite.getDefaultDateField());
            turSNSiteEdit.setDefaultImageField(turSNSite.getDefaultImageField());
            turSNSiteEdit.setDefaultURLField(turSNSite.getDefaultURLField());
            turSNSiteEdit.setExactMatch(turSNSite.getExactMatch());
            turSNSiteEdit.setSearchTemplate(turSNSite.getSearchTemplate());
            // T233 / §VII.6.h — public vs API-key access for the visitor-facing API.
            Optional.ofNullable(turSNSite.getApiAuthMode()).ifPresent(turSNSiteEdit::setApiAuthMode);
            Optional.ofNullable(turSNSite.getTurSNSiteGenAi()).ifPresent(genAi -> {
                TurSNSiteGenAi turSNSiteGenAi = Optional.ofNullable(turSNSiteEdit.getTurSNSiteGenAi())
                        .orElse(new TurSNSiteGenAi());
                // Resolve agent reference from incoming payload — clients may send
                // either a full TurAIAgent or just an object with the id field.
                turSNSiteGenAi.setTurAIAgent(
                        Optional.ofNullable(genAi.getTurAIAgent())
                                .map(a -> a.getId() != null
                                        ? turAIAgentRepository.findById(a.getId()).orElse(null)
                                        : null)
                                .orElse(null));
                turSNSiteGenAi.setSitePrompt(genAi.getSitePrompt());
                // T19 / T24 / T24b — RAG retrieval flags. Previously dropped
                // on update because the controller only copied agent +
                // sitePrompt; the form was sending these all along but the
                // toggles silently lost their value on save. Persist all
                // four together: BM25 fallback gate, hybrid fusion gate,
                // BM25 backing source (EMBEDDED/SE_INSTANCE), and the bound
                // SE instance (resolved by id when source=SE_INSTANCE).
                turSNSiteGenAi.setRagBm25Fallback(genAi.isRagBm25Fallback());
                turSNSiteGenAi.setRagHybridSearch(genAi.isRagHybridSearch());
                turSNSiteGenAi.setRagBm25Source(
                        Optional.ofNullable(genAi.getRagBm25Source())
                                .orElse(com.viglet.turing.persistence.model.rag.TurRagBm25Source.EMBEDDED));
                turSNSiteGenAi.setRagSeInstance(
                        Optional.ofNullable(genAi.getRagSeInstance())
                                .map(se -> se.getId() != null
                                        ? turSEInstanceRepository.findById(se.getId()).orElse(null)
                                        : null)
                                .orElse(null));
                // T383 / §XX.3 — public SN search ranking mode (LEGACY default,
                // HYBRID_RRF opts into BM25 + vector RRF fusion). Distinct from
                // the RAG flags above.
                turSNSiteGenAi.setSnRankingMode(
                        Optional.ofNullable(genAi.getSnRankingMode())
                                .orElse(com.viglet.turing.persistence.model.sn.genai.TurSNRankingMode.LEGACY));
                // T790 / §LIV.1 (Block BF) — knowledge-base mode (VECTOR default,
                // VECTORLESS_STRUCTURED = the copilot path needing only a default LLM,
                // HYBRID = both). Names the retrieval strategy so it is discoverable.
                turSNSiteGenAi.setKnowledgeBaseMode(
                        Optional.ofNullable(genAi.getKnowledgeBaseMode())
                                .orElse(com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode.VECTOR));
                // T472 / §XXVI.9 — index-time content-fit signal (opt-in). The
                // master switch + target-audience persona id drive the
                // deterministic readability scorer at index time.
                turSNSiteGenAi.setContentFitIndexingEnabled(genAi.isContentFitIndexingEnabled());
                turSNSiteGenAi.setContentFitPersonaId(genAi.getContentFitPersonaId());
                // T501 / §X.19 — index-time native video/audio understanding (opt-in,
                // Gemini-only). Appends the clip transcript/scenes to the document text
                // field so the media's content becomes searchable.
                turSNSiteGenAi.setMediaUnderstandingIndexingEnabled(
                        genAi.isMediaUnderstandingIndexingEnabled());
                turSNSiteGenAiRepository.save(turSNSiteGenAi);
                turSNSiteEdit.setTurSNSiteGenAi(turSNSiteGenAi);
            });
            turSNSiteRepository.save(turSNSiteEdit);
            eventPublisher.publishEvent(SnSiteIndexInvalidatedEvent.updated(
                    turSNSiteEdit.getId(), turSNSiteEdit.getName()));
            return turSNSiteMapper.toDto(turSNSiteEdit);
        }).orElse(new TurSNSiteDto()));
    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Site")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "SN_DELETE"})
    public boolean turSNSiteDelete(@PathVariable String id) {
        Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(id);
        turSNSite.ifPresent(site -> {
            TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
            site.getTurSNSiteFields().clear();
            site.getTurSNSiteFieldExts().clear();
            site.getTurSNSiteSpotlights().clear();
            site.getTurSNRankingExpressions().clear();
            site.getTurSNSiteMergeProviders().clear();
            site.setTurSNSiteGenAi(null);
            turSNSiteRepository.flush();
            turSNSiteRepository.delete(site);
            Optional.ofNullable(genAi).ifPresent(turSNSiteGenAiRepository::delete);
        });

        return true;
    }

    @Operation(summary = "Check if a Semantic Navigation Site name already exists")
    @GetMapping("/name-exists")
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public ResponseEntity<Map<String, Boolean>> turSNSiteNameExists(
            @RequestParam String name,
            @RequestParam(required = false) String excludeId) {
        boolean exists = turSNSiteRepository.findByNameIgnoreCase(name)
                .filter(site -> excludeId == null || !site.getId().equals(excludeId))
                .isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @Operation(summary = "Create a Semantic Navigation Site")
    @PostMapping
    @Secured({"ROLE_ADMIN", "SN_CREATE"})
    public ResponseEntity<Object> turSNSiteAdd(@RequestBody TurSNSiteDto turSNSiteDto, Principal principal) {
        if (turSNSiteRepository.findByNameIgnoreCase(turSNSiteDto.getName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(ERROR, "A site with this name already exists."));
        }
        TurSNSite turSNSite = turSNSiteMapper.toEntity(turSNSiteDto);
        if (turSNSite.getTurSNSiteGenAi() != null) {
            turSNSiteGenAiRepository.save(turSNSite.getTurSNSiteGenAi());
        }
        turSNSiteRepository.save(turSNSite);
        turSNTemplate.createSNSite(turSNSite, principal.getName(), Locale.US);
        eventPublisher.publishEvent(SnSiteIndexInvalidatedEvent.created(
                turSNSite.getId(), turSNSite.getName()));
        return ResponseEntity.ok(turSNSiteMapper.toDto(turSNSite));
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public StreamingResponseBody turSNSiteExportAll(HttpServletResponse response) {

        try {
            return turSNSiteExport.exportAll(response);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @GetMapping(value = "/{id}/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public StreamingResponseBody turSNSiteExport(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeContent,
            @RequestParam(defaultValue = "false") boolean includeTemplate,
            @RequestParam(required = false) String taskId,
            HttpServletResponse response) {
        try {
            return turSNSiteExport.exportBySiteId(id, includeContent, includeTemplate, taskId, response);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @PostMapping(value = "/{id}/export/async")
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public Map<String, String> turSNSiteExportAsync(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeTemplate,
            @RequestParam(required = false) String taskId) {
        String effectiveTaskId = taskId != null ? taskId : "export-" + id + "-" + System.currentTimeMillis();
        Thread.ofVirtual().start(() -> {
            try {
                turSNSiteExport.exportBySiteIdAsync(id, true, includeTemplate, effectiveTaskId);
            } catch (Exception e) {
                log.error("Async export failed for site {}: {}", id, e.getMessage(), e);
            }
        });
        return Map.of(TASK_ID, effectiveTaskId);
    }

    @GetMapping(value = "/export/download/{taskId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public StreamingResponseBody downloadExport(@PathVariable String taskId, HttpServletResponse response) {
        return turSNSiteExport.downloadAsyncExport(taskId, response);
    }

    /**
     * Returns the in-flight reindex task id (and progress snapshot) for this
     * site so the admin UI can resume the progress display after navigating
     * away and back. {@code 204 No Content} when no task is running.
     *
     * @since 2026.2.4
     */
    @GetMapping(value = "/{id}/genai/reindex/status")
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public ResponseEntity<Map<String, Object>> turSNSiteGenAiReindexStatus(@PathVariable String id) {
        return contentExchangeService.findActiveReindexTask(id)
                .flatMap(taskId -> contentExchangeService.getProgress(taskId)
                        .map(p -> ResponseEntity.ok(Map.of(
                                TASK_ID, (Object) taskId,
                                "totalDocuments", p.totalDocuments(),
                                "processedDocuments", p.processedDocuments(),
                                "percentage", p.percentage(),
                                "currentLocale", p.currentLocale(),
                                "phase", p.phase(),
                                "estimatedRemainingMillis", p.estimatedRemainingMillis(),
                                "parallelism", contentExchangeService.getParallelism(taskId)))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(value = "/{id}/genai/reindex")
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public ResponseEntity<Map<String, String>> turSNSiteGenAiReindex(@PathVariable String id,
            @RequestParam(required = false) String taskId) {
        return turSNSiteRepository.findById(id)
                .map(site -> {
                    var genAi = site.getTurSNSiteGenAi();
                    var agent = genAi == null ? null : genAi.getTurAIAgent();
                    if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .<Map<String, String>>body(Map.of(ERROR, "RAG is not enabled on this site"));
                    }
                    String effectiveTaskId = taskId != null
                            ? taskId
                            : "rag-reindex-" + id + "-" + System.currentTimeMillis();
                    String siteId = site.getId();
                    Thread.ofVirtual().start(() -> {
                        try {
                            contentExchangeService.reindexVectorStore(siteId, effectiveTaskId);
                        } catch (Exception e) {
                            log.error("Async RAG reindex failed for site {}: {}", siteId, e.getMessage(), e);
                        }
                    });
                    return ResponseEntity.ok(Map.of(TASK_ID, effectiveTaskId));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/export/progress/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public SseEmitter exportProgress(@PathVariable String taskId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        Thread.ofVirtual().start(() -> streamExportProgress(taskId, emitter));
        return emitter;
    }

    private void streamExportProgress(String taskId, SseEmitter emitter) {
        try {
            while (true) {
                var progress = contentExchangeService.getProgress(taskId);
                if (progress.isPresent()) {
                    var p = progress.get();
                    emitter.send(SseEmitter.event()
                            .name("progress")
                            .data(Map.of(
                                    "totalDocuments", p.totalDocuments(),
                                    "processedDocuments", p.processedDocuments(),
                                    "percentage", p.percentage(),
                                    "currentLocale", p.currentLocale(),
                                    "phase", p.phase(),
                                    "estimatedRemainingMillis", p.estimatedRemainingMillis(),
                                    "parallelism", contentExchangeService.getParallelism(taskId)
                            )));
                    if ("completed".equals(p.phase())) {
                        contentExchangeService.removeProgress(taskId);
                        break;
                    }
                }
                Thread.sleep(500);
            }
            emitter.complete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.completeWithError(e);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @Operation(summary = "Semantic Navigation Site Monitoring Status")
    @GetMapping("/{id}/monitoring")
    @Secured({"ROLE_ADMIN", "SN_VIEW"})
    public TurSNSiteMonitoringStatusBean turSNSiteMonitoringStatus(@PathVariable String id) {
        return this.turSNSiteRepository.findById(id).map(turSNSite -> {
            TurSNSiteMonitoringStatusBean turSNSiteMonitoringStatusBean = new TurSNSiteMonitoringStatusBean();
            turSNSiteMonitoringStatusBean.setQueue(turSNQueue.getQueueSize());
            long documentTotal = 0L;
            boolean searchEngineAvailable = true;
            var plugin = pluginFactory.getPluginForSite(turSNSite);
            for (TurSNSiteLocale turSNSiteLocale : turSNSiteLocaleRepository
                    .findByTurSNSite(TurPersistenceUtils.orderByLanguageIgnoreCase(), turSNSite)) {
                try {
                    documentTotal += plugin.getDocumentTotal(turSNSiteLocale);
                } catch (RuntimeException e) {
                    // Search engine is down or circuit breaker is open — degrade gracefully
                    // so the monitoring page still renders queue size and other DB-backed data.
                    searchEngineAvailable = false;
                    log.warn("Search engine unavailable while reading document total for core '{}': {}",
                            turSNSiteLocale.getCore(), e.getMessage());
                }
            }
            turSNSiteMonitoringStatusBean.setDocuments((int) documentTotal);
            turSNSiteMonitoringStatusBean.setSearchEngineAvailable(searchEngineAvailable);
            return turSNSiteMonitoringStatusBean;
        }).orElse(new TurSNSiteMonitoringStatusBean());
    }

}
