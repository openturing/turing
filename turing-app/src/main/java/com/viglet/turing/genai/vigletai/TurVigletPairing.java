/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.vigletai;

/**
 * T957 / §XIV.10.2 — the three payloads the device-code pairing flow exchanges
 * with the console. Grouped in one file because they are one conversation:
 * {@link Session} starts it, {@link Poll} advances it, {@link Status} answers
 * "is this install already paired?" without starting anything.
 *
 * <p>None of them ever carries the credential itself — it is encrypted onto the
 * LLM instance the moment it arrives and never crosses back to the browser.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurVigletPairing {

    private TurVigletPairing() {
    }

    /**
     * What the console shows the operator: a short code to read out and a URL to
     * open. {@code deviceCode} is the opaque handle the install polls with — it
     * is the browser's only job to hand it back, never to display it.
     *
     * @param deviceCode              opaque poll handle
     * @param userCode                short human-readable code (e.g. {@code ABCD-EFGH})
     * @param verificationUri         where the operator signs in and approves
     * @param verificationUriComplete same URL with the code pre-filled; may be null
     * @param intervalSeconds         minimum seconds between polls
     * @param expiresInSeconds        lifetime of this attempt
     */
    public record Session(String deviceCode, String userCode, String verificationUri,
            String verificationUriComplete, int intervalSeconds, int expiresInSeconds) {
    }

    /**
     * The result of one poll. {@code llmInstanceId} and {@code defaultLlmSet} are
     * populated only on {@link TurVigletPairingState#APPROVED} — a half-paired
     * install is worse than an unpaired one, so approval means the whole
     * completion already ran.
     *
     * @param state         where the attempt stands
     * @param llmInstanceId the managed instance created/refreshed, when approved
     * @param defaultLlmSet true when this pairing also became the global default LLM
     */
    public record Poll(TurVigletPairingState state, String llmInstanceId, boolean defaultLlmSet) {

        static Poll pending(TurVigletPairingState state) {
            return new Poll(state, null, false);
        }
    }

    /**
     * Whether this install is already paired, and who it is to the managed
     * service. Safe to expose: the install id identifies the install to Viglet
     * but authorises nothing on its own.
     *
     * @param paired        true when a managed LLM instance exists
     * @param llmInstanceId the managed instance's id, or null
     * @param defaultLlm    true when the managed instance is the global default LLM
     * @param installId     this install's persisted UUID
     */
    public record Status(boolean paired, String llmInstanceId, boolean defaultLlm, String installId) {
    }
}
