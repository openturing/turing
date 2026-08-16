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

    /**
     * T957 / §XIV.10.2 — base URL of the device-code pairing endpoint. The
     * install posts its id and version to {@code {pairingBaseUrl}/device/code}
     * and polls {@code {pairingBaseUrl}/device/token} until the credential
     * arrives; the server half lives in the {@code viglet/cloud} repository.
     */
    private String pairingBaseUrl = "https://llm.viglet.cloud/pair";

    /**
     * T959 / §XIV.10.4 — where a quota refusal points the operator when the free
     * credit runs out. The full target is this URL plus a signed query string
     * naming the install, the tenant and the limit that was reached; blank
     * disables the paid exit entirely (the refusal still states what ran out and
     * still offers the operator's own key).
     */
    private String upgradeBaseUrl = "https://viglet.cloud/upgrade";

    /**
     * T959 — pins the HMAC key used to sign the upgrade target. Left blank (the
     * default) the signature uses the credential this install was paired with in
     * T957, which the managed service issued and can therefore verify. Set this
     * only for a Viglet-side staging environment, or for an install that must
     * offer the paid exit without pairing first.
     */
    private String upgradeSigningSecret = "";

    /**
     * T959 — how long a signed upgrade target stays valid, in seconds. Short
     * enough that a link copied out of a screenshot stops working, long enough
     * that an operator can finish reading the banner and act on it. Values below
     * 60 are raised to 60 at signing time.
     */
    private long upgradeTtlSeconds = 3600;
}
