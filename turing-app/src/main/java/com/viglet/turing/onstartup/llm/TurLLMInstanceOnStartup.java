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
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaDomainExpertise;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaLanguageStyle;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.persona.TurPersonaTone;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
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
    private static final String SEED_PERSONAS_PROPERTY = "turing.startup.default-agent.seed-personas.enabled";
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
    private final TurPersonaRepository turPersonaRepository;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurLLMInstanceOnStartup(Environment environment,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMVendorRepository turLLMVendorRepository,
            TurAIAgentRepository turAIAgentRepository,
            TurPersonaRepository turPersonaRepository,
            TurSecretCryptoService turSecretCryptoService,
            TurGlobalSettingsService turGlobalSettingsService) {
        this.environment = environment;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMVendorRepository = turLLMVendorRepository;
        this.turAIAgentRepository = turAIAgentRepository;
        this.turPersonaRepository = turPersonaRepository;
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

        // Optional soft spend caps (T289–T291). Left null when unset/blank/<= 0,
        // which disables the corresponding gate.
        Double monthlyBudgetUsd = positiveOrNull(environment.getProperty(MONTHLY_BUDGET_PROPERTY));
        Double perTurnSoftCapUsd = positiveOrNull(environment.getProperty(PER_TURN_SOFT_CAP_PROPERTY));
        agent.setMonthlyBudgetUsd(monthlyBudgetUsd);
        agent.setPerTurnSoftCapUsd(perTurnSoftCapUsd);

        turAIAgentRepository.save(agent);

        turGlobalSettingsService.updateDefaultAiAgentId(agent.getId());

        log.info("Bootstrapped default AI agent '{}' (LLM '{}', monthlyBudgetUsd={}, perTurnSoftCapUsd={}) "
                + "and set it as the global default AI agent.",
                agent.getId(), llm.getId(), monthlyBudgetUsd, perTurnSoftCapUsd);

        seedDemoPersonas(agent);
    }

    /**
     * T637 / §XXVII.4 — seeds a small persona catalog onto the freshly-provisioned
     * default agent so the public demo can show "same question, different eyes"
     * (T633) and "validate content as persona X" (T634) out of the box, without a
     * manual admin step. A deliberate mix of speaker + audience personas:
     *
     * <ul>
     *   <li><b>Skeptical Developer</b> ({@code BOTH}) — a terse, technical voice
     *       AND an advanced reader who distrusts marketing fluff;</li>
     *   <li><b>Enterprise Buyer</b> ({@code BOTH}) — an executive voice AND a
     *       business reader scanning for ROI / security / compliance;</li>
     *   <li><b>First-time User</b> ({@code AUDIENCE}) — a novice reader who needs
     *       plain language, expanded acronyms and concrete examples.</li>
     * </ul>
     *
     * <p>No default persona is set — the agent keeps its neutral baseline voice so
     * the picker's "default" option is a real baseline to contrast against. Gated
     * by {@code turing.startup.default-agent.seed-personas.enabled} (default on)
     * and only ever runs on the fresh-agent path (its caller already guards on "no
     * default agent + no agents"), with a persona-table-empty belt so it can never
     * duplicate.
     */
    private void seedDemoPersonas(TurAIAgent agent) {
        if (!environment.getProperty(SEED_PERSONAS_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        if (turPersonaRepository.count() > 0) {
            return;
        }

        TurPersona skepticalDeveloper = buildPersona("Skeptical Developer",
                "A senior engineer who wants precise, verifiable answers and distrusts hype.",
                TurPersonaKind.BOTH, TurPersonaTone.TECHNICAL, TurPersonaLanguageStyle.DIRECT,
                "Answer like a senior engineer: be precise and concrete, cite specifics, and "
                        + "skip marketing language. Say plainly when something is unknown.",
                audience(TurPersonaReadingLevel.GRADUATE, TurPersonaDomainExpertise.ADVANCED,
                        "Comfortable with technical jargon and acronyms.",
                        "Distrusts unbacked claims; wants evidence and edge cases."));
        skepticalDeveloper.setVerbosity(2); // terse voice

        TurPersona enterpriseBuyer = buildPersona("Enterprise Buyer",
                "A decision-maker scanning for business value, security and compliance.",
                TurPersonaKind.BOTH, TurPersonaTone.EXECUTIVE, TurPersonaLanguageStyle.PERSUASIVE,
                "Answer for a busy executive: lead with the outcome and business value, keep it "
                        + "brief, and surface security / compliance / cost implications.",
                audience(TurPersonaReadingLevel.UNDERGRADUATE, TurPersonaDomainExpertise.INTERMEDIATE,
                        "Business vocabulary; limited patience for deep technical detail.",
                        "Skims for ROI, risk and total cost; wary of vague or unbacked claims."));

        TurPersona firstTimeUser = buildPersona("First-time User",
                "Someone new to the product who needs plain, jargon-free explanations.",
                TurPersonaKind.AUDIENCE, null, null, null,
                audience(TurPersonaReadingLevel.MIDDLE, TurPersonaDomainExpertise.NOVICE,
                        "Everyday words only; expand every acronym on first use.",
                        "New to the domain; needs concrete examples and short paragraphs."));

        for (TurPersona persona : new TurPersona[] { skepticalDeveloper, enterpriseBuyer, firstTimeUser }) {
            turPersonaRepository.save(persona);
            agent.getPersonas().add(persona);
        }
        turAIAgentRepository.save(agent);

        log.info("Seeded {} demo personas onto default agent '{}' (catalog: {}).",
                agent.getPersonas().size(), agent.getId(),
                agent.getPersonas().stream().map(TurPersona::getName).toList());
    }

    /** Builds one persona; {@code tone}/{@code languageStyle}/{@code systemInstruction} may be null for pure readers. */
    private static TurPersona buildPersona(String name, String description, TurPersonaKind kind,
            TurPersonaTone tone, TurPersonaLanguageStyle languageStyle,
            String systemInstruction, TurPersonaAudience audience) {
        TurPersona persona = new TurPersona();
        persona.setName(name);
        persona.setDescription(description);
        persona.setPersonaKind(kind);
        persona.setTone(tone);
        persona.setLanguageStyle(languageStyle);
        persona.setSystemInstruction(systemInstruction);
        persona.setEnabled(1);
        // The audience facet is read only for AUDIENCE/BOTH personas (content-fit).
        if (kind == TurPersonaKind.AUDIENCE || kind == TurPersonaKind.BOTH) {
            persona.setAudience(audience);
        }
        return persona;
    }

    /** Builds an audience facet (English reading profile) for a reader persona. */
    private static TurPersonaAudience audience(TurPersonaReadingLevel readingLevel,
            TurPersonaDomainExpertise domainExpertise, String vocabularyCeiling,
            String comprehensionNotes) {
        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(readingLevel);
        audience.setDomainExpertise(domainExpertise);
        audience.setVocabularyCeiling(vocabularyCeiling);
        audience.setComprehensionNotes(comprehensionNotes);
        audience.setPrimaryLanguage("en");
        return audience;
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
