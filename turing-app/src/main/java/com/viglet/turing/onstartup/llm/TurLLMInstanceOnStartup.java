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

import java.util.List;

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
 * Provisions a default {@link TurLLMInstance} from a provider API-key
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
 * <b>Providers.</b> Two provider env vars are supported; the first one set wins
 * (OpenAI takes precedence for backward compatibility):
 * <ul>
 * <li>{@code OPENAI_API_KEY} → OpenAI vendor, default model {@code gpt-4o-mini},</li>
 * <li>{@code GEMINI_API_KEY} → Google Gemini (native) vendor, default model
 * {@code gemini-3.1-flash-lite}.</li>
 * </ul>
 * A single shared model override — {@code turing.startup.default-llm.model} — is
 * applied to whichever provider is provisioned; when it is unset the per-provider
 * default above is used.
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
 * ({@code tenantId = null}, usable by every tenant), the resolved provider's
 * vendor, a cheap chat model (per-provider default, override via
 * {@code turing.startup.default-llm.model}), and is registered as the
 * global default LLM.
 *
 * <p>
 * It then provisions a generic default {@link TurAIAgent} that uses this LLM and
 * registers it as the global Default AI Agent, so a fresh install has a working
 * agent out of the box (and SN sites that defer to the default resolve to it).
 * Both steps are idempotent and never override an existing instance/agent.
 *
 * <p>
 * Optional soft spend caps (T289–T291) can be pre-set on the provisioned agent via
 * {@code turing.startup.default-agent.monthly-budget-usd} and
 * {@code turing.startup.default-agent.per-turn-soft-cap-usd} (both in USD; unset,
 * blank or {@code <= 0} leaves the field null / gate disabled). Useful on shared
 * deployments — notably Viglet Cloud — that want a cost ceiling out of the box.
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
    private static final String MONTHLY_BUDGET_PROPERTY = "turing.startup.default-agent.monthly-budget-usd";
    private static final String PER_TURN_SOFT_CAP_PROPERTY = "turing.startup.default-agent.per-turn-soft-cap-usd";
    private static final String OPENAI_API_KEY_ENV = "OPENAI_API_KEY";
    private static final String OPENAI_VENDOR_ID = "OPENAI";
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    private static final String GEMINI_VENDOR_ID = "GEMINI";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-3.1-flash-lite";
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

        // Resolve the provider from whichever API-key env var is set. OpenAI wins
        // when both are present (backward compatible with the pre-Gemini behavior).
        String openAiKey = environment.getProperty(OPENAI_API_KEY_ENV);
        String geminiKey = environment.getProperty(GEMINI_API_KEY_ENV);
        if (!StringUtils.hasText(openAiKey) && !StringUtils.hasText(geminiKey)) {
            return;
        }

        // Idempotent: only bootstrap when no LLM instance exists yet (fresh install
        // or a wiped demo volume). An existing instance — created here, imported, or
        // added in the admin — is never touched.
        if (!turLLMInstanceRepository.findAll().isEmpty()) {
            return;
        }

        // Shared model override, applied to whichever provider is provisioned. When
        // unset, the per-provider default (gpt-4o-mini / gemini-3.1-flash-lite) wins.
        String modelOverride = environment.getProperty(MODEL_PROPERTY);

        TurLLMInstance instance;
        if (StringUtils.hasText(openAiKey)) {
            instance = provisionInstance(OPENAI_VENDOR_ID, "OpenAI (default)",
                    "Auto-provisioned from " + OPENAI_API_KEY_ENV + " on startup.",
                    OPENAI_BASE_URL, firstNonBlank(modelOverride, DEFAULT_OPENAI_MODEL),
                    openAiKey, OPENAI_API_KEY_ENV);
        } else {
            instance = provisionInstance(GEMINI_VENDOR_ID, "Google Gemini (default)",
                    "Auto-provisioned from " + GEMINI_API_KEY_ENV + " on startup.",
                    null, firstNonBlank(modelOverride, DEFAULT_GEMINI_MODEL),
                    geminiKey, GEMINI_API_KEY_ENV);
        }
        if (instance == null) {
            return;
        }

        provisionDefaultAgent(instance);
    }

    /**
     * Builds, saves and registers a GLOBAL default {@link TurLLMInstance} for the
     * given vendor, encrypting the API key. Returns {@code null} (and logs) when
     * the vendor row is missing. The {@code baseUrl} is set only when non-blank —
     * the native Gemini provider needs no base URL, so it is left null there.
     */
    private TurLLMInstance provisionInstance(String vendorId, String title, String description,
            String baseUrl, String model, String apiKey, String envName) {
        TurLLMVendor vendor = turLLMVendorRepository.findById(vendorId).orElse(null);
        if (vendor == null) {
            log.warn("Cannot bootstrap default LLM instance: vendor '{}' not found.", vendorId);
            return null;
        }

        TurLLMInstance instance = new TurLLMInstance();
        instance.setTitle(title);
        instance.setDescription(description);
        instance.setTurLLMVendor(vendor);
        instance.setModelName(model);
        if (StringUtils.hasText(baseUrl)) {
            instance.setUrl(baseUrl);
        }
        instance.setEnabled(1);
        instance.setToolsEnabled(true);
        instance.setApiKeyEncrypted(turSecretCryptoService.encrypt(apiKey.trim()));
        // GLOBAL instance (tenantId left null) — usable by every tenant.
        turLLMInstanceRepository.save(instance);

        turGlobalSettingsService.updateDefaultLlmId(instance.getId());

        log.info("Bootstrapped default {} LLM instance '{}' (model '{}') from {} and set it as the "
                + "global default LLM.", vendorId, instance.getId(), model, envName);
        return instance;
    }

    /**
     * Ensures a global Default AI Agent exists and has the just-provisioned LLM
     * wired, so a fresh install has a working agent (and SN sites that defer to
     * the default resolve to it) without a manual admin step.
     *
     * <p>T655 — this is <em>import-aware</em>. Because this runner is {@code @Order(3)},
     * an imported seed bundle ({@code TurExportImportOnStartup}, {@code @Order(2)})
     * has already run: it may have brought its own agent (e.g. the public demo's
     * agent + persona catalog) — but never the OpenAI API key, which lives only in
     * the environment here. So rather than only creating a generic agent, we:
     * <ol>
     *   <li>keep an already-set default agent untouched (just wire the LLM if it
     *       has none);</li>
     *   <li>otherwise <b>adopt an imported agent</b> as the global default (and wire
     *       the env-key LLM onto it) — this is what lets the demo ship its agent +
     *       personas as importable content instead of product startup code;</li>
     *   <li>otherwise create the generic default agent (the fresh-install path).</li>
     * </ol>
     * The whole method only runs on a fresh/wiped install (its caller gates on an
     * empty LLM table), so it never disturbs an established deployment.
     */
    private void provisionDefaultAgent(TurLLMInstance llm) {
        TurAIAgent defaultAgent = resolveOrCreateDefaultAgent(llm);
        if (defaultAgent != null) {
            ensureLlmWired(defaultAgent, llm);
        }
    }

    /**
     * Returns the agent that should be the global default, creating the generic
     * one only when nothing else is available. Adopts an imported agent (setting
     * it as the global default) when one exists and no default is set yet.
     */
    private TurAIAgent resolveOrCreateDefaultAgent(TurLLMInstance llm) {
        String defaultAgentId = turGlobalSettingsService.getDefaultAiAgentId();
        if (StringUtils.hasText(defaultAgentId)) {
            return turAIAgentRepository.findById(defaultAgentId).orElse(null);
        }
        List<TurAIAgent> agents = turAIAgentRepository.findAll();
        if (!agents.isEmpty()) {
            TurAIAgent adopted = agents.get(0);
            turGlobalSettingsService.updateDefaultAiAgentId(adopted.getId());
            log.info("Adopted imported agent '{}' ('{}') as the global default AI agent{}.",
                    adopted.getId(), adopted.getTitle(),
                    agents.size() > 1 ? " (first of " + agents.size() + " imported agents)" : "");
            return adopted;
        }
        return createGenericDefaultAgent(llm);
    }

    /** Creates the generic helpful-assistant agent for a truly fresh install. */
    private TurAIAgent createGenericDefaultAgent(TurLLMInstance llm) {
        TurAIAgent agent = new TurAIAgent();
        agent.setTitle(DEFAULT_AGENT_TITLE);
        agent.setDescription("Auto-provisioned default AI agent on startup.");
        agent.setSystemPrompt(DEFAULT_AGENT_PROMPT);
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        // The auto-provisioned default agent is the T622 fail-open fallback for
        // the zero-config public demo's SN chat — an anonymous, un-configured
        // surface answering questions about a crawled site. Lock it to
        // STRICT_RAG so it is grounded out of the box (no code generation /
        // off-topic tasks). This only affects the SN RAG path; general agent
        // chat / MCP ignore the mode, so reusing this agent elsewhere is
        // unaffected. Operators can flip it to OPEN in the UI.
        agent.setGroundingMode(com.viglet.turing.persistence.model.agent.TurAgentGroundingMode.STRICT_RAG);
        agent.getLlmInstances().add(llm);

        // Optional soft spend caps (T289–T291). Left null when unset/blank/<= 0,
        // which disables the corresponding gate.
        Double monthlyBudgetUsd = positiveOrNull(environment.getProperty(MONTHLY_BUDGET_PROPERTY));
        Double perTurnSoftCapUsd = positiveOrNull(environment.getProperty(PER_TURN_SOFT_CAP_PROPERTY));
        agent.setMonthlyBudgetUsd(monthlyBudgetUsd);
        agent.setPerTurnSoftCapUsd(perTurnSoftCapUsd);

        turAIAgentRepository.save(agent);
        turGlobalSettingsService.updateDefaultAiAgentId(agent.getId());

        log.info("Bootstrapped generic default AI agent '{}' (LLM '{}', monthlyBudgetUsd={}, "
                + "perTurnSoftCapUsd={}) and set it as the global default AI agent.",
                agent.getId(), llm.getId(), monthlyBudgetUsd, perTurnSoftCapUsd);
        return agent;
    }

    /**
     * Wires {@code llm} onto {@code agent} when the agent has no LLM yet (e.g. an
     * imported agent, whose bundle never carries the API key). A no-op when the
     * agent already has one, so a self-created agent is not double-wired.
     */
    private void ensureLlmWired(TurAIAgent agent, TurLLMInstance llm) {
        if (agent.getLlmInstances() != null && !agent.getLlmInstances().isEmpty()) {
            return;
        }
        agent.getLlmInstances().add(llm);
        turAIAgentRepository.save(agent);
        log.info("Wired default LLM '{}' onto default agent '{}'.", llm.getId(), agent.getId());
    }

    /** Returns the first argument that has text, or the last one as a fallback. */
    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    /**
     * Parses a configured budget value: unset, blank, non-numeric or non-positive
     * (<= 0, the "gate disabled" sentinel) all collapse to {@code null} so the
     * agent field stays null and the corresponding soft-budget gate is off.
     */
    private static Double positiveOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            log.warn("Ignoring non-numeric default-agent budget value '{}'.", value);
            return null;
        }
    }
}
