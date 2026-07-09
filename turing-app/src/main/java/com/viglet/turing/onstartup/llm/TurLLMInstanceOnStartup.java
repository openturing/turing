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
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Provisions a default OpenAI {@link TurLLMInstance} from the
 * {@code OPENAI_API_KEY}
 * environment variable on startup, so a fresh deployment (notably the public
 * demo —
 * {@code turing-demo.viglet.org}) has a working chat/RAG LLM without a manual
 * admin
 * step. The API key already reaches the container as an env var (Viglet Cloud
 * {@code docker-compose} passes {@code OPENAI_API_KEY} to the demo service);
 * this bean
 * turns that env var into a configured, encrypted, default-selected LLM
 * instance.
 *
 * <p>
 * Runs after {@link com.viglet.turing.onstartup.TurExportImportOnStartup}
 * ({@code @Order(2)}) via {@code @Order(3)} so a seeded export ZIP (which may
 * bring its
 * own LLM instance) is imported first.
 *
 * <p>
 * <b>Gate + idempotency.</b> It acts only when ALL of these hold, so it never
 * overrides an existing setup and never fires on deployments that don't want
 * it:
 * <ul>
 * <li>{@code turing.startup.default-llm.enabled} is not {@code false} (default
 * on),</li>
 * <li>{@code OPENAI_API_KEY} is set and non-blank,</li>
 * <li>the instance table is empty (no LLM instance exists yet).</li>
 * </ul>
 * The empty-table guard mirrors the other {@code *OnStartup} vendor seeders and
 * makes
 * this safe to run on every boot: once an instance exists (created here,
 * imported, or
 * added in the admin) it is left untouched. On a demo volume wipe + re-seed the
 * table
 * is empty again, so the default is recreated automatically.
 *
 * <p>
 * The created instance is a platform-provided GLOBAL one
 * ({@code tenantId = null},
 * usable by every tenant), OpenAI vendor, cheap chat model (default
 * {@code gpt-4o-mini},
 * override via {@code turing.startup.default-llm.model}), and is registered as
 * the
 * global default LLM.
 *
 * <p>
 * It then provisions a generic default {@link TurAIAgent} that uses this LLM and
 * registers it as the global Default AI Agent, so a fresh install has a working
 * agent out of the box (and SN sites that defer to the default resolve to it).
 * Both steps are idempotent and never override an existing instance/agent.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
@Transactional
@Order(3)
public class TurLLMInstanceOnStartup implements ApplicationRunner {

    private static final String ENABLED_PROPERTY = "turing.startup.default-llm.enabled";
    private static final String MODEL_PROPERTY = "turing.startup.default-llm.model";
    private static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";
    private static final String OPENAI_VENDOR_ID = "OPENAI";
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_AGENT_TITLE = "Default Agent";
    private static final String DEFAULT_AGENT_PROMPT = """
            You are a helpful assistant. Answer the user's questions clearly and \
            concisely. When a knowledge-base search tool is available, ground your \
            answers in the retrieved content and say so when the answer isn't found.""";

    private final Environment environment;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurLLMVendorRepository turLLMVendorRepository;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurLLMInstanceOnStartup(Environment environment,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMVendorRepository turLLMVendorRepository,
            TurAIAgentRepository turAIAgentRepository,
            TurSecretCryptoService turSecretCryptoService,
            TurGlobalSettingsService turGlobalSettingsService) {
        this.environment = environment;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMVendorRepository = turLLMVendorRepository;
        this.turAIAgentRepository = turAIAgentRepository;
        this.turSecretCryptoService = turSecretCryptoService;
        this.turGlobalSettingsService = turGlobalSettingsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }

        String apiKey = environment.getProperty(OPENAI_API_KEY_ENV);
        if (!StringUtils.hasText(apiKey)) {
            return;
        }

        // Idempotent: only bootstrap when no LLM instance exists yet (fresh install
        // or a wiped demo volume). An existing instance — created here, imported, or
        // added in the admin — is never touched.
        if (!turLLMInstanceRepository.findAll().isEmpty()) {
            return;
        }

        TurLLMVendor openAiVendor = turLLMVendorRepository.findById(OPENAI_VENDOR_ID).orElse(null);
        if (openAiVendor == null) {
            log.warn("Cannot bootstrap default OpenAI LLM instance: vendor '{}' not found.",
                    OPENAI_VENDOR_ID);
            return;
        }

        String model = environment.getProperty(MODEL_PROPERTY, DEFAULT_MODEL);

        TurLLMInstance instance = new TurLLMInstance();
        instance.setTitle("OpenAI (default)");
        instance.setDescription("Auto-provisioned from OPENAI_API_KEY on startup.");
        instance.setTurLLMVendor(openAiVendor);
        instance.setModelName(model);
        instance.setUrl(OPENAI_BASE_URL);
        instance.setEnabled(1);
        instance.setToolsEnabled(true);
        instance.setApiKeyEncrypted(turSecretCryptoService.encrypt(apiKey.trim()));
        // GLOBAL instance (tenantId left null) — usable by every tenant.
        turLLMInstanceRepository.save(instance);

        turGlobalSettingsService.updateDefaultLlmId(instance.getId());

        log.info("Bootstrapped default OpenAI LLM instance '{}' (model '{}') from {} and set it as the "
                + "global default LLM.", instance.getId(), model, OPENAI_API_KEY_ENV);

        provisionDefaultAgent(instance);
    }

    /**
     * Creates a generic default {@link TurAIAgent} using the just-provisioned LLM
     * and registers it as the global Default AI Agent, so a fresh install has a
     * working agent (and SN sites that defer to the default resolve to it) without
     * a manual admin step. Skipped when a default agent is already set or any agent
     * already exists — never overrides an existing setup.
     */
    private void provisionDefaultAgent(TurLLMInstance llm) {
        if (StringUtils.hasText(turGlobalSettingsService.getDefaultAiAgentId())
                || !turAIAgentRepository.findAll().isEmpty()) {
            return;
        }

        TurAIAgent agent = new TurAIAgent();
        agent.setTitle(DEFAULT_AGENT_TITLE);
        agent.setDescription("Auto-provisioned default AI agent on startup.");
        agent.setSystemPrompt(DEFAULT_AGENT_PROMPT);
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        agent.getLlmInstances().add(llm);
        turAIAgentRepository.save(agent);

        turGlobalSettingsService.updateDefaultAiAgentId(agent.getId());

        log.info("Bootstrapped default AI agent '{}' (LLM '{}') and set it as the global default AI agent.",
                agent.getId(), llm.getId());
    }
}
