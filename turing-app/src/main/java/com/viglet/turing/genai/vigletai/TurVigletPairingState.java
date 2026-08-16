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
 * T957 / §XIV.10.2 — where one device-code pairing attempt stands, as reported
 * by a poll. Mirrors the RFC 8628 token-endpoint error vocabulary, which is what
 * the managed service answers with.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurVigletPairingState {

    /** The operator has not approved yet — keep polling at the given interval. */
    PENDING,

    /** Polling too fast; back off by one second and keep going. */
    SLOW_DOWN,

    /** Approved and completed: the credential is stored and the instance exists. */
    APPROVED,

    /** The operator declined the approval. Stop polling. */
    DENIED,

    /** The device code timed out before approval. Start over. */
    EXPIRED;

    /** True when the client should stop polling — nothing more will change. */
    public boolean isTerminal() {
        return this == APPROVED || this == DENIED || this == EXPIRED;
    }
}
