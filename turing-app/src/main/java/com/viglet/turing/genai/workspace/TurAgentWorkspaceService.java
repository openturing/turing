/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.workspace;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.service.storage.TurStorageContentTypes;
import com.viglet.turing.service.storage.TurStorageService;

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link TurAgentWorkspace} implementation — a thin façade over the
 * pluggable {@link TurStorageService} (T111).
 *
 * <p>Every operation maps a workspace-relative {@code key} onto the T78
 * multi-tenant storage path
 * {@code tenants/{agentId}/{conversationId}/workspace/<key>}, so two
 * conversations (or two agents) never see each other's blobs. Path-traversal
 * is rejected before any storage call (see {@link #sanitizeKey} /
 * {@link #sanitizeSegment}); the filesystem backend additionally re-checks via
 * {@code resolveSafe()}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurAgentWorkspaceService implements TurAgentWorkspace {

    static final String TENANTS_DIR = "tenants";
    static final String WORKSPACE_DIR = "workspace";
    private static final String FILE_SERVE_PATH = "/api/v2/workspace/file";

    private final TurStorageService storageService;
    private final TurAgentWorkspaceUrlSigner urlSigner;
    private final TurWorkspaceEventBus eventBus;

    public TurAgentWorkspaceService(TurStorageService storageService,
            TurAgentWorkspaceUrlSigner urlSigner,
            TurWorkspaceEventBus eventBus) {
        this.storageService = storageService;
        this.urlSigner = urlSigner;
        this.eventBus = eventBus;
    }

    @Override
    public void put(String agentId, String conversationId, String key, byte[] content, String contentType) {
        if (!storageService.isEnabled()) {
            throw new IllegalStateException(
                    "Agent workspace requires storage to be enabled (turing.storage.type != none).");
        }
        byte[] body = content == null ? new byte[0] : content;
        String safeAgent = sanitizeSegment(agentId);
        String safeConv = sanitizeSegment(conversationId);
        String safeKey = sanitizeKey(key);
        String objectName = scopeRoot(safeAgent, safeConv) + safeKey;
        String type = (contentType == null || contentType.isBlank())
                ? TurStorageContentTypes.guessContentType(key)
                : contentType;
        try (InputStream in = new ByteArrayInputStream(body)) {
            storageService.uploadStream(objectName, in, body.length, type);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write workspace key: " + key, e);
        }
        // T113 — advertise the artifact's new metadata on the workspace SSE bus.
        // Routed by the raw conversationId so it matches the SSE subscriber's
        // query param; only metadata + a signed URL travel, never the bytes.
        eventBus.publish(TurWorkspaceEvent.put(conversationId, safeKey, type, body.length,
                buildSignedUrl(safeAgent, safeConv, safeKey)));
    }

    @Override
    public Optional<byte[]> get(String agentId, String conversationId, String key) {
        if (!storageService.isEnabled()) {
            return Optional.empty();
        }
        String objectName = scopedKey(agentId, conversationId, key);
        try (InputStream in = storageService.downloadObject(objectName)) {
            return Optional.of(in.readAllBytes());
        } catch (Exception e) {
            // Missing key, disabled storage, or backend error — callers treat
            // all three as "not present" rather than propagating.
            log.debug("Workspace get miss for key '{}': {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<WorkspaceEntry> list(String agentId, String conversationId, String prefix) {
        if (!storageService.isEnabled()) {
            return List.of();
        }
        String safeAgent = sanitizeSegment(agentId);
        String safeConv = sanitizeSegment(conversationId);
        String scopeRoot = scopeRoot(safeAgent, safeConv);
        String listPrefix = (prefix == null || prefix.isBlank())
                ? scopeRoot
                : scopeRoot + sanitizeKey(prefix);
        List<TurAssetItem> files = new ArrayList<>();
        collectRecursively(listPrefix, files);

        List<WorkspaceEntry> entries = new ArrayList<>();
        for (TurAssetItem item : files) {
            String relativeKey = stripScope(item.name(), scopeRoot);
            if (relativeKey == null) {
                continue;
            }
            entries.add(new WorkspaceEntry(
                    relativeKey,
                    item.size(),
                    item.contentType(),
                    item.lastModified(),
                    buildSignedUrl(safeAgent, safeConv, relativeKey)));
        }
        return entries;
    }

    @Override
    public void delete(String agentId, String conversationId, String key) {
        if (!storageService.isEnabled()) {
            return;
        }
        String safeKey = sanitizeKey(key);
        String objectName = scopeRoot(sanitizeSegment(agentId), sanitizeSegment(conversationId)) + safeKey;
        try {
            storageService.deleteObject(objectName);
            // T113 — broadcast the removal so connected portals drop the
            // artifact from their list. Published only on a successful delete.
            eventBus.publish(TurWorkspaceEvent.delete(conversationId, safeKey));
        } catch (Exception e) {
            log.debug("Workspace delete no-op for key '{}': {}", key, e.getMessage());
        }
    }

    @Override
    public String signedUrl(String agentId, String conversationId, String key) {
        return buildSignedUrl(sanitizeSegment(agentId), sanitizeSegment(conversationId), sanitizeKey(key));
    }

    // ---------------------------------------------------------------------
    // Path scoping + sanitization (shared with TurAgentWorkspaceFileAPI)
    // ---------------------------------------------------------------------

    /** Storage object name for a key under {@code tenants/{a}/{c}/workspace/}. */
    static String scopedKey(String agentId, String conversationId, String key) {
        return scopeRoot(sanitizeSegment(agentId), sanitizeSegment(conversationId)) + sanitizeKey(key);
    }

    private static String scopeRoot(String safeAgent, String safeConv) {
        return TENANTS_DIR + "/" + safeAgent + "/" + safeConv + "/" + WORKSPACE_DIR + "/";
    }

    /**
     * Sanitizes an agent/conversation id used as a single path segment.
     * Ids are server-minted UUIDs ({@code [a-zA-Z0-9-]}); anything else is
     * coerced or rejected (defense-in-depth against a future API relaxing the
     * format). Mirrors {@code TurCodeInterpreterToolService.sanitizePathSegment}.
     */
    static String sanitizeSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Workspace tenant id must not be blank");
        }
        String safe = raw.replaceAll("[^a-zA-Z0-9._-]", "");
        if (safe.isEmpty() || ".".equals(safe) || "..".equals(safe)) {
            throw new IllegalArgumentException("Invalid workspace tenant id: " + raw);
        }
        return safe;
    }

    /**
     * Validates + normalizes a workspace-relative key. Folders are allowed
     * via {@code /}; {@code ..}, absolute paths, and the signer delimiter
     * {@code |} are rejected so a key can neither escape its scope nor corrupt
     * the HMAC payload.
     */
    static String sanitizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Workspace key must not be blank");
        }
        String normalized = key.replace('\\', '/').strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Workspace key must not be blank");
        }
        if (normalized.contains("|")) {
            throw new IllegalArgumentException("Workspace key must not contain '|': " + key);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("Path traversal rejected in workspace key: " + key);
            }
        }
        return normalized;
    }

    /**
     * Recursively walks {@code listObjects} (which is single-level on both the
     * filesystem and MinIO backends) so the result spans nested folders while
     * staying scoped to this conversation's subtree — cheaper and safer than
     * {@code listAllObjects} (which would scan every tenant).
     */
    private void collectRecursively(String prefix, List<TurAssetItem> acc) {
        for (TurAssetItem item : storageService.listObjects(prefix)) {
            if (item.directory()) {
                String childPrefix = item.name().endsWith("/") ? item.name() : item.name() + "/";
                // Guard against a backend echoing the prefix back as a dir
                // entry (would otherwise recurse forever).
                if (!childPrefix.equals(prefix)) {
                    collectRecursively(childPrefix, acc);
                }
            } else {
                acc.add(item);
            }
        }
    }

    /** Strips the scope prefix from a storage object name → workspace key. */
    private static String stripScope(String objectName, String scopeRoot) {
        if (objectName == null) {
            return null;
        }
        String name = objectName.replace('\\', '/');
        if (!name.startsWith(scopeRoot)) {
            return null;
        }
        String rel = name.substring(scopeRoot.length());
        return rel.isBlank() ? null : rel;
    }

    private String buildSignedUrl(String safeAgent, String safeConv, String safeKey) {
        String suffix = urlSigner.signQueryString(safeAgent, safeConv, safeKey);
        String base = FILE_SERVE_PATH
                + "?agentId=" + enc(safeAgent)
                + "&conversationId=" + enc(safeConv)
                + "&key=" + enc(safeKey);
        if (suffix == null || suffix.isEmpty()) {
            return base;
        }
        // signQueryString returns "?exp=...&sig=..." — splice as extra params.
        return base + "&" + suffix.substring(1);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
