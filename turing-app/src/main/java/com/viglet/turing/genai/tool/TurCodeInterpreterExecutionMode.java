package com.viglet.turing.genai.tool;

import java.util.Locale;

/**
 * Where the Code Interpreter runs the LLM-/Custom-Tool-generated Python.
 *
 * <ul>
 *   <li>{@link #NATIVE} — the legacy (and default) path. Python runs as a
 *       host subprocess under the JVM's OS user. Fast, zero external
 *       dependency, but offers <b>no runtime isolation</b>: a malicious
 *       script can {@code open("../../../etc/passwd")} or read any file
 *       the JVM can. Path isolation (T78) + signed URLs (T79) close the
 *       operational / HTTP vectors but not the runtime escape.</li>
 *   <li>{@link #DOCKER} — T80. Each execution runs inside a throwaway
 *       container (bind-mounting only its own session directory, dropping
 *       all Linux capabilities, no-new-privileges, optional no-network).
 *       This is the runtime security boundary needed before exposing the
 *       Code Interpreter to untrusted / multi-tenant customer-authored
 *       Custom Tools.</li>
 * </ul>
 *
 * <p>The mode is an operator choice exposed in Console → Global Settings →
 * Code Interpreter, so a deployment can keep the current native behavior
 * (e.g. local dev, single-tenant on-prem) or opt into containerization
 * (multi-tenant / hardened) without a code change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurCodeInterpreterExecutionMode {
    NATIVE,
    DOCKER;

    /** Default when the setting is missing or unparseable — preserves legacy behavior. */
    public static final TurCodeInterpreterExecutionMode DEFAULT = NATIVE;

    /**
     * Lenient parse: case-insensitive, null/blank/unknown → {@link #DEFAULT}.
     * Keeps a corrupt config row from breaking the sandbox entirely (it
     * just falls back to the native path).
     */
    public static TurCodeInterpreterExecutionMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }
}
