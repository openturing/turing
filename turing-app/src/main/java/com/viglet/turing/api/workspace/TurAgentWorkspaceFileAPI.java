/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.workspace;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.TurAgentWorkspaceUrlSigner;
import com.viglet.turing.genai.workspace.TurAgentWorkspaceUrlSigner.VerifyResult;
import com.viglet.turing.service.storage.TurStorageContentTypes;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves agent-workspace blobs (T111) addressed by the HMAC-signed URLs that
 * {@link TurAgentWorkspace#signedUrl} mints.
 *
 * <p>The signature gate runs <b>before</b> any storage lookup so a 403 never
 * leaks whether a key exists. Every non-OK {@link VerifyResult} collapses to a
 * single 403 — the granular case is logged, not surfaced, so an attacker can't
 * probe which check fails first.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/workspace")
@Tag(name = "Agent Workspace Files", description = "Serve blobs stored in a per-conversation agent workspace")
public class TurAgentWorkspaceFileAPI {

    private final TurAgentWorkspace workspace;
    private final TurAgentWorkspaceUrlSigner urlSigner;

    public TurAgentWorkspaceFileAPI(TurAgentWorkspace workspace, TurAgentWorkspaceUrlSigner urlSigner) {
        this.workspace = workspace;
        this.urlSigner = urlSigner;
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> getFile(
            @RequestParam String agentId,
            @RequestParam String conversationId,
            @RequestParam String key,
            @RequestParam(value = "exp", required = false) String exp,
            @RequestParam(value = "sig", required = false) String sig) {

        // The signer signs the sanitized values; the service sanitizes again on
        // read, so verify against the raw params exactly as they were signed.
        VerifyResult verdict = urlSigner.verify(agentId, conversationId, key, exp, sig);
        if (verdict != VerifyResult.OK) {
            log.warn("[WorkspaceFileAPI] rejecting {}/{}/{}: {}", agentId, conversationId, key, verdict);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        final byte[] bytes;
        try {
            bytes = workspace.get(agentId, conversationId, key).orElse(null);
        } catch (IllegalArgumentException e) {
            // Sanitization rejected a malformed id/key — treat as bad request.
            log.warn("[WorkspaceFileAPI] malformed request {}/{}/{}: {}",
                    agentId, conversationId, key, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }

        String filename = key.substring(key.replace('\\', '/').lastIndexOf('/') + 1);
        String mimeType = TurStorageContentTypes.guessContentType(key);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(new ByteArrayResource(bytes));
    }
}
