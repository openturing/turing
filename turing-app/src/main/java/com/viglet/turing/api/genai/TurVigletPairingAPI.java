/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.genai;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.vigletai.TurVigletPairing;
import com.viglet.turing.genai.vigletai.TurVigletPairingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T957 / §XIV.10.2 — the three calls the console drives device-code pairing
 * with: ask whether this install is already paired, start an attempt, and poll
 * it until the credential arrives.
 *
 * <p>The credential itself never reaches the browser — {@code poll} answers with
 * a state and, on approval, the id of the LLM instance it created. Creating an
 * LLM instance and setting the global default is what pairing does, so the calls
 * carry the same authority the LLM admin surface does.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/genai/viglet-ai/pairing")
@Tag(name = "Viglet AI Pairing", description = "Device-code pairing with the managed Viglet AI endpoint")
public class TurVigletPairingAPI {

    /** Poll request body — the handle {@code start} returned. */
    public record PollRequest(String deviceCode) {
    }

    private final TurVigletPairingService pairingService;

    public TurVigletPairingAPI(TurVigletPairingService pairingService) {
        this.pairingService = pairingService;
    }

    @Operation(summary = "Whether this install is already paired with Viglet AI")
    @GetMapping("/status")
    @Secured({ "ROLE_ADMIN", "LLM_VIEW" })
    public TurVigletPairing.Status status() {
        return pairingService.status();
    }

    @Operation(summary = "Start a pairing attempt — returns the user code and verification URL")
    @PostMapping("/start")
    @Secured({ "ROLE_ADMIN", "LLM_CREATE" })
    public TurVigletPairing.Session start() {
        return pairingService.start();
    }

    @Operation(summary = "Poll a pairing attempt; on approval the managed LLM instance already exists")
    @PostMapping("/poll")
    @Secured({ "ROLE_ADMIN", "LLM_CREATE" })
    public TurVigletPairing.Poll poll(@RequestBody PollRequest request) {
        return pairingService.poll(request == null ? null : request.deviceCode());
    }
}
