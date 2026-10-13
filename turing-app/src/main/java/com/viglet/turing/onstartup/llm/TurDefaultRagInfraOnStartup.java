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
package com.viglet.turing.onstartup.llm;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceEmbeddingModelFactory;
import com.viglet.turing.genai.provider.llm.TurLocalEmbeddingModelFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * T622 / §XXVII.2 — provisions a zero-config <b>local RAG backend</b> at startup
 * so the public demo's grounded chat has a retrieval store without any manual
 * admin step or external embedding API/key.
 *
 * <p>
 * The demo seed export (T620) is <em>search-only</em>: it populates the search
 * engine so live search works, but ships no embedding model / vector store, so
 * the hero <em>chat</em> would have nothing to retrieve. The default agent
 * provisioned by {@link TurLLMInstanceOnStartup} (T621) likewise has an LLM but
 * no RAG infrastructure. This runner closes that gap by creating:
 * <ul>
 * <li>a {@code HUGGINGFACE} {@link TurEmbeddingModel} (T629) — an in-process
 * ONNX sentence-transformers model picked by <b>repo id</b> (default
 * {@code sentence-transformers/all-MiniLM-L6-v2}, 384-dim), resolved to its
 * {@code .onnx}/{@code tokenizer.json} and cached; no LLM instance, no key, no
 * per-token cost. So the seeded default is byte-shape identical to a
 * user-picked HuggingFace model. Blanking
 * {@code turing.startup.default-rag.model-repo-id} falls back to a
 * {@code TRANSFORMERS_LOCAL} model built from raw {@code model-path}/
 * {@code tokenizer-path} URLs (offline mirror / custom artifact);</li>
 * <li>a {@code LUCENE} {@link TurStoreInstance} — the embedded Lucene vector
 * store;</li>
 * </ul>
 * registers both as the global embedding/store defaults, and wires them onto the
 * global Default AI Agent (enabling RAG). It then, on a background thread,
 * <b>warms up the embedding model (the ONNX download happens here, at startup)</b>
 * and triggers a reindex-with-embeddings of every SN site's already-imported
 * content — the import ran before the embedding model existed, so the store
 * starts empty and must be back-filled.
 *
 * <p>
 * <b>Ordering.</b> {@code @Order(4)} runs after {@link TurLLMInstanceOnStartup}
 * ({@code @Order(3)}) so the Default AI Agent already exists to wire onto, and
 * after {@link com.viglet.turing.onstartup.TurExportImportOnStartup}
 * ({@code @Order(2)}) so the seed content is already imported into the search
 * engine (the reindex source).
 *
 * <p>
 * <b>Gate + idempotency.</b> It runs when {@code turing.startup.default-rag.enabled}
 * is not {@code false} (default on) and a global Default AI Agent is configured,
 * and it never overrides an existing setup: an agent that already has an
 * embedding model and a store is left alone.
 *
 * <p>
 * <b>Each piece converges on its own (T1088).</b> The runner used to skip
 * everything when any embedding model or store existed, which held only on a
 * fresh install: the seed ships its own "Default Vector Store", so the agent got
 * neither piece and chat stayed off. Now the embedding model is the global
 * default when one is set, else a new local ONNX model when none exists; the
 * store is the global default, else an existing enabled Lucene store, else a new
 * one when none exists. Whichever the agent lacks is wired, RAG is turned on and
 * the content reindexed only when this run completed the wiring, and each step
 * logs what it did or why it stopped.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@Transactional
@Order(4)
public class TurDefaultRagInfraOnStartup implements ApplicationRunner {

    private static final String ENABLED_PROPERTY = "turing.startup.default-rag.enabled";
    private static final String REINDEX_PROPERTY = "turing.startup.default-rag.reindex";
    private static final String MODEL_NAME_PROPERTY = "turing.startup.default-rag.model-name";
    private static final String MODEL_REPO_ID_PROPERTY = "turing.startup.default-rag.model-repo-id";
    private static final String MODEL_PATH_PROPERTY = "turing.startup.default-rag.model-path";
    private static final String TOKENIZER_PATH_PROPERTY = "turing.startup.default-rag.tokenizer-path";
    private static final String STORE_PATH_PROPERTY = "turing.startup.default-rag.store-path";

    private static final String LUCENE_VENDOR_ID = "LUCENE";
    private static final String DEFAULT_MODEL_NAME = "all-MiniLM-L6-v2";
    // T629 — the default is now a HuggingFace repo id (same in-process ONNX
    // runtime as the raw-URL local path, but stored as a picked model so the
    // seeded default and a user-picked HuggingFace model are byte-shape identical).
    private static final String DEFAULT_MODEL_REPO_ID = "sentence-transformers/all-MiniLM-L6-v2";
    // Fallback raw URLs (used only when model-repo-id is explicitly blanked):
    // smallest widely-used sentence-transformers ONNX export (~80 MB, 384-dim).
    private static final String DEFAULT_MODEL_PATH =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx";
    private static final String DEFAULT_TOKENIZER_PATH =
            "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json";
    private static final String DEFAULT_STORE_PATH = "./store/lucene-vector";

    private final Environment environment;
    private final TurEmbeddingModelRepository turEmbeddingModelRepository;
    private final TurStoreInstanceRepository turStoreInstanceRepository;
    private final TurStoreVendorRepository turStoreVendorRepository;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurLocalEmbeddingModelFactory localEmbeddingModelFactory;
    private final TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory;
    private final TurSNSiteContentExchangeService contentExchangeService;

    public TurDefaultRagInfraOnStartup(Environment environment,
            TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurStoreInstanceRepository turStoreInstanceRepository,
            TurStoreVendorRepository turStoreVendorRepository,
            TurAIAgentRepository turAIAgentRepository,
            TurSNSiteRepository turSNSiteRepository,
            TurGlobalSettingsService turGlobalSettingsService,
            TurLocalEmbeddingModelFactory localEmbeddingModelFactory,
            TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory,
            TurSNSiteContentExchangeService contentExchangeService) {
        this.environment = environment;
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turStoreVendorRepository = turStoreVendorRepository;
        this.turAIAgentRepository = turAIAgentRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.localEmbeddingModelFactory = localEmbeddingModelFactory;
        this.huggingFaceEmbeddingModelFactory = huggingFaceEmbeddingModelFactory;
        this.contentExchangeService = contentExchangeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }

        String defaultAgentId = turGlobalSettingsService.getDefaultAiAgentId();
        if (!StringUtils.hasText(defaultAgentId)) {
            log.info("Default local RAG backend skipped: no global Default AI Agent to wire onto.");
            return;
        }
        TurAIAgent agent = turAIAgentRepository.findById(defaultAgentId).orElse(null);
        if (agent == null) {
            log.warn("Cannot bootstrap default local RAG backend: default agent '{}' not found.", defaultAgentId);
            return;
        }
        // T1088 — converge each piece the agent lacks, instead of skipping the whole
        // bootstrap when any embedding model or store exists: the seed ships its
        // "Default Vector Store", and the all-or-nothing guard left the agent with
        // neither model nor store. An agent already wired is left as it was.
        if (agent.getTurEmbeddingModelInstance() != null && agent.getTurStoreInstance() != null) {
            log.debug("Default local RAG backend: default agent '{}' already has an embedding model and a store.",
                    agent.getId());
            return;
        }
        if (agent.getTurEmbeddingModelInstance() == null) {
            TurEmbeddingModel model = ensureEmbeddingModel();
            if (model == null) {
                return;
            }
            agent.setTurEmbeddingModelInstance(model);
        }
        if (agent.getTurStoreInstance() == null) {
            TurStoreInstance store = ensureStore();
            if (store == null) {
                return;
            }
            agent.setTurStoreInstance(store);
        }
        agent.setRagEnabled(true);
        turAIAgentRepository.save(agent);
        TurEmbeddingModel embeddingModel = agent.getTurEmbeddingModelInstance();
        TurStoreInstance storeInstance = agent.getTurStoreInstance();
        log.info("Default local RAG backend: wired embedding model '{}' and store '{}' onto default agent '{}' "
                + "with RAG enabled.", embeddingModel.getId(), storeInstance.getId(), agent.getId());
        startWarmUpAndReindex(embeddingModel);
    }

    /**
     * The embedding model the default agent gets: the global default when one is
     * set and enabled, a new local ONNX model when none exists at all, and none
     * (logged) when models exist but none is the default, since picking one of
     * somebody's models is not this runner's call.
     */
    private TurEmbeddingModel ensureEmbeddingModel() {
        String defaultId = turGlobalSettingsService.getDefaultEmbeddingModelId();
        if (StringUtils.hasText(defaultId)) {
            TurEmbeddingModel byDefault = turEmbeddingModelRepository.findById(defaultId)
                    .filter(model -> model.getEnabled() == 1).orElse(null);
            if (byDefault != null) {
                log.info("Default local RAG backend: adopting the global default embedding model '{}'.", defaultId);
                return byDefault;
            }
        }
        long existing = turEmbeddingModelRepository.count();
        if (existing > 0) {
            log.info("Default local RAG backend skipped: {} embedding model(s) exist and none is the enabled "
                    + "global default, so none is wired onto the default agent.", existing);
            return null;
        }
        TurEmbeddingModel created = createEmbeddingModel();
        turGlobalSettingsService.updateDefaultEmbeddingModelId(created.getId());
        log.info("Default local RAG backend: created embedding model '{}' ({}).", created.getId(),
                created.getModelName());
        return created;
    }

    /**
     * The store the default agent gets: the global default when set and enabled,
     * else an enabled embedded Lucene store already there (the seed's platform
     * default), else a new one when no store exists at all.
     */
    private TurStoreInstance ensureStore() {
        String defaultId = turGlobalSettingsService.getDefaultEmbeddingStoreId();
        if (StringUtils.hasText(defaultId)) {
            TurStoreInstance byDefault = turStoreInstanceRepository.findById(defaultId)
                    .filter(store -> store.getEnabled() == 1).orElse(null);
            if (byDefault != null) {
                log.info("Default local RAG backend: adopting the global default store '{}'.", defaultId);
                return byDefault;
            }
        }
        TurStoreInstance lucene = turStoreInstanceRepository.findAll().stream()
                .filter(store -> store.getEnabled() == 1 && store.getTurStoreVendor() != null
                        && LUCENE_VENDOR_ID.equals(store.getTurStoreVendor().getId()))
                .findFirst().orElse(null);
        if (lucene != null) {
            turGlobalSettingsService.updateDefaultEmbeddingStoreId(lucene.getId());
            log.info("Default local RAG backend: adopting the existing Lucene store '{}' ({}).", lucene.getId(),
                    lucene.getTitle());
            return lucene;
        }
        long existing = turStoreInstanceRepository.count();
        if (existing > 0) {
            log.info("Default local RAG backend skipped: {} store(s) exist, none is an enabled Lucene store or "
                    + "the global default, so none is wired onto the default agent.", existing);
            return null;
        }
        TurStoreVendor luceneVendor = turStoreVendorRepository.findById(LUCENE_VENDOR_ID).orElse(null);
        if (luceneVendor == null) {
            log.warn("Cannot bootstrap default local RAG backend: store vendor '{}' not found.", LUCENE_VENDOR_ID);
            return null;
        }
        String storePath = environment.getProperty(STORE_PATH_PROPERTY, DEFAULT_STORE_PATH);
        TurStoreInstance storeInstance = new TurStoreInstance();
        storeInstance.setTitle("Lucene (default)");
        storeInstance.setDescription("Auto-provisioned embedded Lucene vector store on startup.");
        storeInstance.setTurStoreVendor(luceneVendor);
        storeInstance.setUrl(storePath);
        storeInstance.setEnabled(1);
        // GLOBAL instance (tenantId left null).
        turStoreInstanceRepository.save(storeInstance);
        turGlobalSettingsService.updateDefaultEmbeddingStoreId(storeInstance.getId());
        log.info("Default local RAG backend: created Lucene store '{}' at '{}'.", storeInstance.getId(), storePath);
        return storeInstance;
    }

    private TurEmbeddingModel createEmbeddingModel() {
        String modelName = environment.getProperty(MODEL_NAME_PROPERTY, DEFAULT_MODEL_NAME);
        String modelRepoId = environment.getProperty(MODEL_REPO_ID_PROPERTY, DEFAULT_MODEL_REPO_ID);
        String modelPath = environment.getProperty(MODEL_PATH_PROPERTY, DEFAULT_MODEL_PATH);
        String tokenizerPath = environment.getProperty(TOKENIZER_PATH_PROPERTY, DEFAULT_TOKENIZER_PATH);

        TurEmbeddingModel embeddingModel = new TurEmbeddingModel();
        embeddingModel.setModelName(modelName);
        embeddingModel.setEnabled(1);
        if (StringUtils.hasText(modelRepoId)) {
            // T629 — default path: a HUGGINGFACE model storing just the repo id,
            // so the seeded default and a user-picked HuggingFace model are the
            // same shape. Same in-process ONNX runtime as the raw-URL path.
            embeddingModel.setDescription("Auto-provisioned HuggingFace ONNX embedding model on startup.");
            embeddingModel.setProviderType(TurHuggingFaceEmbeddingModelFactory.PROVIDER_TYPE);
            embeddingModel.setModelReference(modelRepoId);
        } else {
            // Fallback: raw ONNX/tokenizer URLs (offline mirror, custom artifact)
            // — kept for installs that blank model-repo-id and point at their own.
            embeddingModel.setDescription("Auto-provisioned local ONNX embedding model on startup.");
            embeddingModel.setProviderType(TurLocalEmbeddingModelFactory.PROVIDER_TYPE);
            embeddingModel.setModelPath(modelPath);
            embeddingModel.setTokenizerPath(tokenizerPath);
        }
        // GLOBAL instance (tenantId left null) — usable by every tenant.
        turEmbeddingModelRepository.save(embeddingModel);
        return embeddingModel;
    }

    private void startWarmUpAndReindex(TurEmbeddingModel embeddingModel) {
        boolean reindex = environment.getProperty(REINDEX_PROPERTY, Boolean.class, Boolean.TRUE);
        // Warm-up (ONNX download) + reindex are slow and network-bound; run them
        // off the startup thread so app readiness isn't blocked. A daemon thread
        // is enough — losing an in-flight reindex on shutdown is harmless (it is
        // re-triggered on the next boot). Start it only AFTER this provisioning
        // transaction commits, so the reindex sees the agent's wired-up
        // embedding model + store (otherwise it would race the commit and skip
        // the site as "not RAG-ready").
        TurEmbeddingModel embeddingSnapshot = embeddingModel;
        Runnable startWorker = () -> {
            Thread worker = new Thread(() -> warmUpAndReindex(embeddingSnapshot, reindex),
                    "startup-rag-provision");
            worker.setDaemon(true);
            worker.start();
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    startWorker.run();
                }
            });
        } else {
            startWorker.run();
        }
    }

    /**
     * Background: force the ONNX model download/load once (so the first chat
     * turn isn't slow), then reindex every SN site's imported content into the
     * freshly-wired vector store. Each step is best-effort — a failure (e.g. no
     * network on an offline install) is logged, never fatal.
     */
    private void warmUpAndReindex(TurEmbeddingModel embeddingModel, boolean reindex) {
        try {
            log.info("Warming up local ONNX embedding model (downloading '{}' if not cached)…",
                    embeddingModel.getModelName());
            if (huggingFaceEmbeddingModelFactory.supports(embeddingModel.getProviderType())) {
                huggingFaceEmbeddingModelFactory.resolve(embeddingModel);
            } else {
                localEmbeddingModelFactory.resolve(embeddingModel);
            }
            log.info("Local ONNX embedding model ready.");
        } catch (RuntimeException e) {
            log.warn("Local ONNX embedding model warm-up failed: {} — grounded chat will retry on first use.",
                    e.getMessage());
            return;
        }

        if (!reindex) {
            return;
        }
        for (TurSNSite site : turSNSiteRepository.findAll()) {
            String taskId = "startup-rag-reindex-" + site.getId();
            try {
                int reindexed = contentExchangeService.reindexVectorStore(site.getId(), taskId);
                log.info("Startup RAG reindex for site '{}': {} documents embedded.",
                        site.getName(), reindexed);
            } catch (RuntimeException e) {
                log.warn("Startup RAG reindex failed for site '{}': {}", site.getName(), e.getMessage());
            } finally {
                contentExchangeService.removeProgress(taskId);
            }
        }
    }
}
