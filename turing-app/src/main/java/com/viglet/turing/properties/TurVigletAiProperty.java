/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T956 / §XIV.10.1 — configuration for <b>Viglet AI</b>, the managed LLM
 * endpoint behind the onboarding credit (Block J.10).
 *
 * <p>Both values are deliberately <b>deployment-level, not per-instance</b>: the
 * whole point of the {@code viglet} provider is that an operator never types an
 * endpoint or a model name. They exist here only so a Viglet-side staging
 * environment can be pointed somewhere else without editing an LLM instance.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.viglet-ai")
public class TurVigletAiProperty {

    /**
     * The managed endpoint, in OpenAI wire format (Block AZ's governed gateway,
     * run by Viglet). Fixed for the install — the LLM instance's {@code url} and
     * its {@code baseUrl} provider option are ignored by the {@code viglet}
     * provider.
     */
    private String baseUrl = "https://llm.viglet.cloud/v1";

    /**
     * The single opaque model alias the managed endpoint serves. Naming the
     * vendor model here would pin Turing to one vendor's price sheet and invite
     * the per-token comparison this credit is designed not to have; the alias
     * lets the service move behind it.
     */
    private String modelAlias = "viglet-fast";
}
