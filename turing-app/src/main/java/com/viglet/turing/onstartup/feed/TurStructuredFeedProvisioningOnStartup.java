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
package com.viglet.turing.onstartup.feed;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.genai.feed.TurStructuredFeedIngestService;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.properties.TurStructuredFeedProperty;
import com.viglet.turing.properties.TurStructuredFeedProperty.Source;
import com.viglet.turing.sn.manifest.TurSNSiteManifestService;

import lombok.extern.slf4j.Slf4j;

/**
 * T796 / §LIV.8 (Block BF) — startup auto-provisioning of a config-driven
 * <strong>Vectorless (Structured-Data) RAG</strong> knowledge base.
 *
 * <p>For each structured-feed {@link Source} declaring {@code provision: true},
 * this runner turns a pure-env-var deployment into a working vectorless KB with no
 * manual admin step: when the SN site does not yet exist it is created via
 * {@link TurSNSiteManifestService#provision} (needs the source's {@code seInstanceId}),
 * its GenAI binding is set to {@link TurSNKnowledgeBaseMode#VECTORLESS_STRUCTURED},
 * and the first ingest runs immediately (converging the field schema from the
 * source's manifest and importing the feed). On a site that already exists it only
 * re-asserts the Vectorless mode (idempotent) and leaves ongoing refresh to the
 * scheduled {@link TurStructuredFeedIngestService}.
 *
 * <p><b>Product-generic:</b> nothing here is tied to any specific catalog — the
 * concrete site name, feed URL, manifest and SE instance all come from
 * {@code turing.genai.structured-feed} config, so the real values live only in a
 * deployment's environment.
 *
 * <p><b>Ordering + gate.</b> {@code @Order(5)} runs after
 * {@link com.viglet.turing.onstartup.llm.TurDefaultRagInfraOnStartup}
 * ({@code @Order(4)}) so default data + SE instances already exist. It acts only
 * when the feature is enabled ({@code turing.genai.structured-feed.enabled}) and
 * a source opts in with {@code provision: true}. Fully fail-open: any per-source
 * error is logged and never blocks application startup.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@Order(5)
public class TurStructuredFeedProvisioningOnStartup implements ApplicationRunner {

    /** SE vendor id of the embedded Apache Lucene engine (seeded by TurSEVendorOnStartup). */
    private static final String LUCENE_VENDOR_ID = "LUCENE";
    /** Default on-disk directory for the auto-created Lucene SE instance's cores. */
    private static final String DEFAULT_LUCENE_SE_PATH = "./store/lucene-se";

    private final TurStructuredFeedProperty property;
    private final TurSNSiteManifestService manifestService;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteGenAiRepository turSNSiteGenAiRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final TurSEVendorRepository turSEVendorRepository;
    private final TurStructuredFeedIngestService ingestService;

    public TurStructuredFeedProvisioningOnStartup(TurStructuredFeedProperty property,
            TurSNSiteManifestService manifestService,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteGenAiRepository turSNSiteGenAiRepository,
            TurSEInstanceRepository turSEInstanceRepository,
            TurSEVendorRepository turSEVendorRepository,
            TurStructuredFeedIngestService ingestService) {
        this.property = property;
        this.manifestService = manifestService;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteGenAiRepository = turSNSiteGenAiRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.turSEVendorRepository = turSEVendorRepository;
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!property.isEnabled() || property.getSources() == null) {
            return;
        }
        for (Source source : property.getSources()) {
            if (source == null || !source.isEnabled() || !source.isProvision()) {
                continue;
            }
            try {
                provisionSource(source);
            } catch (RuntimeException e) {
                log.warn("[StructuredFeed] startup provisioning failed for source '{}': {}",
                        source.getId(), e.getMessage(), e);
            }
        }
    }

    private void provisionSource(Source source) {
        if (!StringUtils.hasText(source.getSiteName())) {
            log.warn("[StructuredFeed] provision skipped — source '{}' has no siteName", source.getId());
            return;
        }
        boolean exists = turSNSiteRepository.findByNameIgnoreCase(source.getSiteName()).isPresent();
        if (!exists) {
            // Resolve the backing SE instance: an explicit seInstanceId when given,
            // otherwise a default embedded Lucene instance (found or created) so a
            // zero-config deployment needs no SE setup at all.
            String seInstanceId = resolveSeInstanceId(source);
            if (!StringUtils.hasText(seInstanceId)) {
                log.warn("[StructuredFeed] cannot create site '{}' — no SE instance could be resolved for source '{}'",
                        source.getSiteName(), source.getId());
                return;
            }
            // Create the bare site (base fields + first locale/core). The declared
            // field schema is converged by the ingest below, from the source's
            // manifest — so field parsing stays in one place (the ingester).
            VigletFieldManifest manifest = new VigletFieldManifest(
                    source.getSiteName(),
                    StringUtils.hasText(source.getDescription()) ? source.getDescription()
                            : "Auto-provisioned Vectorless (Structured-Data) RAG knowledge base.",
                    seInstanceId,
                    "1",
                    List.of(source.getLocale()),
                    List.of(),
                    List.of());
            VigletManifestResult result = manifestService.provision(manifest);
            log.info("[StructuredFeed] provisioned SN site '{}' (id={}) for vectorless KB source '{}'",
                    source.getSiteName(), result.siteId(), source.getId());
        }

        setVectorlessMode(source.getSiteName());

        // Seed the KB immediately only when we just created it; an already-existing
        // site is kept fresh by the scheduled ingester on its normal interval.
        if (!exists) {
            TurStructuredFeedIngestService.IngestResult ingest = ingestService.ingestSource(source);
            log.info("[StructuredFeed] initial ingest for provisioned source '{}' → {}",
                    source.getId(), ingest);
        }
    }

    /**
     * Resolve the SE instance id backing a new site: the source's explicit
     * {@code seInstanceId} when set, otherwise a default embedded Lucene instance
     * (reused if one already exists, created otherwise) — so a deployment can
     * provision a vectorless KB with no SE configuration at all.
     */
    private String resolveSeInstanceId(Source source) {
        if (StringUtils.hasText(source.getSeInstanceId())) {
            return source.getSeInstanceId();
        }
        return findOrCreateLuceneSeInstance();
    }

    private String findOrCreateLuceneSeInstance() {
        List<TurSEInstance> existing = turSEInstanceRepository.findByTurSEVendor_Id(LUCENE_VENDOR_ID);
        if (existing != null && !existing.isEmpty()) {
            return existing.get(0).getId();
        }
        TurSEVendor lucene = turSEVendorRepository.findById(LUCENE_VENDOR_ID).orElse(null);
        if (lucene == null) {
            log.warn("[StructuredFeed] cannot default to Lucene SE — vendor '{}' not seeded", LUCENE_VENDOR_ID);
            return null;
        }
        TurSEInstance instance = new TurSEInstance();
        instance.setTitle("Lucene (default)");
        instance.setDescription("Auto-provisioned embedded Lucene search engine for vectorless KBs.");
        instance.setTurSEVendor(lucene);
        instance.setEndpointUrl(DEFAULT_LUCENE_SE_PATH);
        instance.setEnabled(1);
        turSEInstanceRepository.save(instance);
        log.info("[StructuredFeed] created default Lucene SE instance '{}' at '{}'",
                instance.getId(), DEFAULT_LUCENE_SE_PATH);
        return instance.getId();
    }

    /**
     * Set the site's GenAI binding to {@link TurSNKnowledgeBaseMode#VECTORLESS_STRUCTURED},
     * creating a minimal GenAI record when the site has none (the copilot needs no
     * agent — only a default LLM — so an agent-less binding is valid). Uses the
     * fetch-joined read so the LAZY GenAI association is resolved.
     */
    private void setVectorlessMode(String siteName) {
        TurSNSite site = turSNSiteRepository.findByNameIgnoreCase(siteName)
                .map(TurSNSite::getId)
                .flatMap(turSNSiteRepository::findByIdWithGenAi)
                .orElse(null);
        if (site == null) {
            return;
        }
        TurSNSiteGenAi genAi = site.getTurSNSiteGenAi();
        if (genAi == null) {
            genAi = new TurSNSiteGenAi();
            genAi.setKnowledgeBaseMode(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);
            turSNSiteGenAiRepository.save(genAi);
            site.setTurSNSiteGenAi(genAi);
            turSNSiteRepository.save(site);
        } else if (genAi.getKnowledgeBaseMode() != TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED) {
            genAi.setKnowledgeBaseMode(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);
            turSNSiteGenAiRepository.save(genAi);
        }
    }
}
