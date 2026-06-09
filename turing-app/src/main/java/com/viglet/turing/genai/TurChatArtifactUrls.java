/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes sandboxed-artifact URLs in an assistant's final text before it is
 * sent to the chat client.
 *
 * <h2>Why</h2>
 *
 * The code-interpreter tool surfaces generated files (charts, PDFs, CSVs) to
 * the LLM as markdown. Turing adopts OpenAI's {@code sandbox:} virtual scheme
 * for those URLs because OpenAI-family models (gpt-4o-mini et al.) habitually
 * prefix file URLs with {@code sandbox:} — it matches their own Code Interpreter
 * training. The trouble: a raw {@code sandbox:} URL is not loadable by a browser
 * and react-markdown's default sanitizer drops the unknown scheme, so an inline
 * {@code ![chart](sandbox:/api/v2/code-interpreter/…)} renders with an empty
 * {@code src} (the recurring "image won't render" reports).
 *
 * <p>The client-side resolvers ({@code resolveSandboxUrl} in the admin console
 * and {@code @viglet/turing-sdk}) handle this, but they depend on the consuming
 * bundle being up to date. This server-side pass is the deployment-proof
 * backstop: it rewrites {@code sandbox:/api/…} (and accidental
 * {@code sandbox:sandbox:/api/…} or {@code sandbox://api/…} variants) down to a
 * plain, same-origin-relative {@code /api/…} path in the final assistant text,
 * so the image renders with <em>any</em> markdown renderer — no client change
 * required. Cross-origin SDK embedders still resolve the relative path against
 * their configured Turing base via {@code resolveSandboxUrl}.
 *
 * <p>The rewrite is scoped to URLs that point at Turing's own API
 * ({@code /api/…}); a {@code sandbox:} prefix in front of anything else is left
 * untouched. Text with no {@code sandbox:} occurrence is returned unchanged
 * (cheap pre-check), so this is a safe no-op on every non-code-interpreter turn.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class TurChatArtifactUrls {

    /**
     * One-or-more {@code sandbox:} prefixes (each with optional following
     * slashes) immediately preceding a Turing API path. The lookahead keeps
     * the {@code /api/} boundary in place so only the scheme is stripped.
     */
    private static final Pattern SANDBOX_API_PREFIX =
            Pattern.compile("(?:sandbox:/*)+(?=/api/)", Pattern.CASE_INSENSITIVE);

    private TurChatArtifactUrls() {
    }

    /**
     * Strips the {@code sandbox:} virtual scheme from Turing API artifact URLs
     * in {@code text}, leaving a same-origin-relative {@code /api/…} path.
     *
     * @param text the assistant's final reply (may be null/blank)
     * @return the normalized text, or {@code text} unchanged when it carries no
     *         {@code sandbox:} occurrence
     */
    public static String normalize(String text) {
        if (text == null || text.length() < "sandbox:".length()) {
            return text;
        }
        // Cheap guard: skip the regex machinery on the overwhelming majority of
        // turns that never mention the scheme (case-insensitive).
        if (!text.toLowerCase(Locale.ROOT).contains("sandbox:")) {
            return text;
        }
        return SANDBOX_API_PREFIX.matcher(text).replaceAll("");
    }
}
