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
 * T641 / §XXXVII.3 — app-layer abuse controls for the anonymous public chat /
 * search surface ({@code turing.abuse.*}). The public SN endpoints are
 * {@code permitAll} by design and the shipped reverse proxy provided no
 * throttling, so a loop of {@code POST /api/sn/{site}/chat/conversation} could
 * drive unbounded LLM spend. These knobs add a per-IP / per-session request
 * rate limit (default ON, in-process) plus an optional hard month-to-date LLM
 * cost circuit-breaker for the anonymous chat path (default OFF — a USD ceiling
 * is deployment-specific).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.abuse")
public class TurAbuseControlProperty {

    /** Anonymous chat/search rate-limit settings ({@code turing.abuse.chat.*}). */
    private Chat chat = new Chat();

    @Getter
    @Setter
    public static class Chat {

        /** Master switch for the in-process per-IP/session rate limiter (default on). */
        private boolean rateLimitEnabled = true;

        /** Max requests per rolling window per client IP. {@code <= 0} disables the IP dimension. */
        private int requestsPerMinutePerIp = 60;

        /** Max requests per rolling window per session/conversation. {@code <= 0} disables it. */
        private int requestsPerMinutePerSession = 30;

        /** Window length in seconds for the fixed-window counters. */
        private int windowSeconds = 60;

        /**
         * Hard month-to-date LLM spend ceiling (USD) for the anonymous SN chat
         * path. When {@code > 0} and total month-to-date cost reaches it, new
         * anonymous conversations are refused with a friendly message and NO
         * upstream LLM call fires (a hard kill-switch, unlike the soft
         * per-agent {@code TurChatCostBudgetGate}). {@code 0} (default) = off.
         */
        private double hardMonthlyCapUsd = 0d;

        /**
         * T647 / §XXXVII.9 — tool trust boundary for the anonymous public SN chat.
         * When {@code true} (default, legacy) an anonymous visitor's turn may
         * invoke the agent's tools (native / MCP-client / custom / skill) exactly
         * as before. Set {@code false} to strip ALL tool callbacks on the
         * anonymous SN chat path so an untrusted visitor (or an injection payload
         * in retrieved content) cannot trigger tool execution with system
         * authority. Authenticated agent/persona chat is unaffected.
         */
        private boolean anonymousToolsEnabled = true;

        // ---- T648 / §XXXVII.10 — input / DoS caps (default-on) ----------------

        /**
         * Max size (bytes) of a single anonymous slot-upload / slot-extract file,
         * enforced BEFORE Tika parsing + any vision call. Default 10 MB.
         * {@code <= 0} disables the per-file gate.
         */
        private long maxUploadBytes = 10L * 1024 * 1024;

        /** Max messages accepted in one anonymous conversation turn. Default 100. {@code <= 0} = unbounded. */
        private int maxMessagesPerTurn = 100;

        /** Max characters in a single anonymous chat message. Default 24000. {@code <= 0} = unbounded. */
        private int maxMessageChars = 24_000;

        /** Max entries in an imported ZIP (page / skill). Default 5000. {@code <= 0} = unbounded. */
        private int maxZipEntries = 5_000;

        /** Max uncompressed bytes of a single ZIP entry (zip-bomb guard). Default 50 MB. {@code <= 0} = unbounded. */
        private long maxZipEntryBytes = 50L * 1024 * 1024;

        /** Max total uncompressed bytes across an imported ZIP. Default 250 MB. {@code <= 0} = unbounded. */
        private long maxZipTotalBytes = 250L * 1024 * 1024;
    }
}
