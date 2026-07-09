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

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Provisions a default OpenAI {@link TurLLMInstance} from the {@code OPENAI_API_KEY}
 * environment variable on startup, so a fresh deployment (notably the public demo —
 * {@code turing-demo.viglet.org}) has a working chat/RAG LLM without a manual admin
 * step. The API key already reaches the container as an env var (Viglet Cloud
 * {@code docker-compose} passes {@code OPENAI_API_KEY} to the demo service); this bean
 * turns that env var into a configured, encrypted, default-selected LLM instance.
 *
 * <p>Runs after {@link com.viglet.turing.onstartup.TurExportImportOnStartup}
 * ({@code @Order(2)}) via {@code @Order(3)} so a seeded export ZIP (which may bring its
 * own LLM instance) is imported first.
 *
 * <p><b>Gate + idempotency.</b> It acts only when ALL of these hold, so it never
 * overrides an existing setup and never fires on deployments that don't want it:
 * <ul>
 *   <li>{@code turing.startup.default-llm.enabled} is not {@code false} (default on),</li>
 *   <li>{@code OPENAI_API_KEY} is set and non-blank,</li>
 *   <li>the instance table is empty (no LLM instance exists yet).</li>
 * </ul>
 * The empty-table guard mirrors the other {@code *OnStartup} vendor seeders and makes
 * this safe to run on every boot: once an instance exists (created here, imported, or
 * added in the admin) it is left untouched. On a demo volume wipe + re-seed the table
 * is empty again, so the default is recreated automatically.
 *
 * <p>The created instance is a platform-provided GLOBAL one ({@code tenantId = null},
 * usable by every tenant), OpenAI vendor, cheap chat model (default {@code gpt-4o-mini},
 * override via {@code turing.startup.default-llm.model}), and is registered as the
 * global default LLM.
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

    private final Environment environment;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurLLMVendorRepository turLLMVendorRepository;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurLLMInstanceOnStartup(Environment environment,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMVendorRepository turLLMVendorRepository,
            TurSecretCryptoService turSecretCryptoService,
            TurGlobalSettingsService turGlobalSettingsService) {
        this.environment = environment;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMVendorRepository = turLLMVendorRepository;
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
    }
}
