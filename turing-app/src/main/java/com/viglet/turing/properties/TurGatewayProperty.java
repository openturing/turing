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
 * T740 / §XLIX — configuration for the Governed LLM Gateway (Block AZ): the
 * OpenAI-compatible inbound egress surface ({@code /v1/chat/completions},
 * {@code /v1/embeddings}, {@code /v1/models}).
 *
 * <p>The whole gateway is <b>opt-in and default-off</b> ({@code turing.gateway.enabled=false}):
 * the controllers are {@code @ConditionalOnProperty} on this flag, so when it is
 * off the endpoints do not exist and nothing changes for any non-gateway path
 * (no existing IT re-validates). Turn it on to let any OpenAI-compatible client
 * point its {@code base_url} at Turing.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.gateway")
public class TurGatewayProperty {

    /**
     * Master switch for the OpenAI-compatible inbound gateway. Default {@code false}
     * — the {@code /v1/*} endpoints are not registered unless this is {@code true}.
     */
    private boolean enabled = false;

    /**
     * When {@code true} (default), the gateway requires a valid virtual key
     * ({@code Authorization: Bearer sk-turing-...}, see T741). Set {@code false}
     * only in a trusted, network-isolated deployment where the {@code /v1/*}
     * surface is reachable solely by already-authenticated callers. Has no effect
     * until the T741 virtual-key filter is in place.
     */
    private boolean requireVirtualKey = true;

    /**
     * T743 / §XLIX — TTL (seconds) for a gateway response cached via the
     * {@code x-turing-cache: semantic} header. Default 300s.
     */
    private long cacheTtlSeconds = 300;

    /**
     * T743 / §XLIX — max entries in the gateway response cache (LRU-evicted past
     * this bound). Default 1000.
     */
    private int cacheMaxEntries = 1000;

    /**
     * T746 / §XLIX — capture inbound gateway traffic (base-model + router turns)
     * as rows of a reusable {@code TurEvalDataset} so real production traffic can
     * be replayed through the Block AJ eval graders and feed the distillation
     * bridge ("the gateway that learns"). Default off — capturing prompts/answers
     * is opt-in and admin-managed.
     */
    private boolean captureTraffic = false;

    /** T746 — display name of the eval dataset gateway traffic is captured into. */
    private String captureDatasetName = "Gateway Traffic";
}
