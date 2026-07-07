/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.util.regex.Pattern;

/**
 * Turns a raw tool-input string into a short, secret-free digest suitable for
 * the {@code "tool_call"} SSE event (T436) and the read-only trace (T427).
 *
 * <p>Tool arguments routinely carry credentials (a Custom Tool that proxies a
 * first-party API may receive an {@code apiKey}/{@code token}). The live event
 * must <em>never</em> put those on the wire, so values of keys that look like
 * secrets are masked before truncation. The result is a best-effort,
 * single-line digest — not a parseable payload.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurToolArgsSummary {

    /** Hard ceiling on the digest length; long JSON args are elided with an ellipsis. */
    static final int MAX_LENGTH = 256;

    /**
     * Matches a {@code "key": "value"} / {@code key=value} pair whose key looks
     * like a secret, capturing the key prefix so only the value is masked.
     */
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(\"?(?:api[_-]?key|apikey|access[_-]?key|secret[_-]?key|token|secret|passwo?rd|"
                    + "authorization|auth|credential|bearer)\"?\\s*[:=]\\s*)"
                    + "(\"[^\"]*\"|'[^']*'|[^\\s,}\\]]+)");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TurToolArgsSummary() {
    }

    /**
     * @param input the raw tool input (typically a JSON object string), nullable
     * @return a masked, whitespace-collapsed, length-capped digest; empty string
     *         when the input is null/blank
     */
    public static String summarize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String redacted = SECRET.matcher(input).replaceAll("$1\"***\"");
        redacted = WHITESPACE.matcher(redacted).replaceAll(" ").trim();
        if (redacted.length() > MAX_LENGTH) {
            return redacted.substring(0, MAX_LENGTH) + "…";
        }
        return redacted;
    }
}
