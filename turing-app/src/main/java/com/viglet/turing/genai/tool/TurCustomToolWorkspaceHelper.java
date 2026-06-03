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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.WorkspaceEntry;

import lombok.extern.slf4j.Slf4j;

/**
 * Conversation-scoped helper exposed to Custom Tool Groovy scripts under the
 * binding variable {@code workspace} (T112). Wraps {@link TurAgentWorkspace}
 * (T111) so a tool can persist drafts, partial results, and intermediate
 * artifacts to its per-conversation blob store, then read them back on a later
 * turn — and hand the LLM an HMAC-signed URL the user can download.
 *
 * <h2>Workspace vs slots</h2>
 * {@code slots} ({@link TurCustomToolSlotHelper}) holds flat scalars/JSON that
 * the React portal polls; {@code workspace} holds <b>byte-blobs</b> (PDFs,
 * CSVs, images, markdown) addressed by {@code key} and servable via a signed
 * URL. A tool that builds a CSV writes it to the workspace and drops the
 * {@code workspace.url(key)} into a slot or its reply — the bytes never bloat
 * the prompt.
 *
 * <h2>Scope</h2>
 * Each helper instance is pre-bound to the active {@code agentId} +
 * {@code conversationId}, pulled out of the Spring AI {@code ToolContext} by
 * {@link TurCustomToolCallbackService} (keys
 * {@link TurCustomToolCallbackService#TOOL_CONTEXT_AGENT_ID} /
 * {@link TurCustomToolCallbackService#TOOL_CONTEXT_CONVERSATION_ID}). The
 * underlying {@link TurAgentWorkspace} lands every blob under the T78
 * multi-tenant path {@code tenants/{agentId}/{conversationId}/workspace/<key>},
 * so two conversations (or two agents) never see each other's artifacts.
 *
 * <h2>No-op mode</h2>
 * When the tool is invoked outside an active chat session (e.g. a standalone
 * unit test or the admin preview path), {@code agentId} / {@code conversationId}
 * are {@code null}. Rather than throw, every operation degrades to a safe
 * no-op: {@link #put} / {@link #delete} log and return, {@link #get} /
 * {@link #url} return {@code null}, and {@link #list} returns an empty list.
 * This mirrors {@link TurCustomToolSlotHelper}'s defensive behaviour so a
 * script that mixes {@code workspace} and {@code slots} can still be previewed.
 *
 * <p>Usage in Groovy:
 * <pre>{@code
 *   // Build a CSV with the code helper, copy it into the workspace:
 *   def r = code.executePythonStructured('''
 *       import csv
 *       with open('report.csv', 'w') as f:
 *           csv.writer(f).writerow(['lead', 'score'])
 *   ''')
 *   def bytes = http.getBytes(r.files()[0].url())
 *   workspace.put("reports/lead-export-${slots.get('export_date')}.csv", bytes, "text/csv")
 *
 *   // Later turn — another tool reads it back, or hands the LLM a download URL:
 *   def previous = workspace.get("reports/lead-export-2026-06-03.csv")
 *   return "Your export is ready: " + workspace.url("reports/lead-export-2026-06-03.csv")
 * }</pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurCustomToolWorkspaceHelper {

    private final TurAgentWorkspace workspace;
    private final String agentId;
    private final String conversationId;

    /**
     * Back-compat constructor for unit tests that exercise the helper without
     * tenant context — every operation runs in no-op mode.
     */
    public TurCustomToolWorkspaceHelper(TurAgentWorkspace workspace) {
        this(workspace, null, null);
    }

    /**
     * Production constructor used by {@link TurCustomToolCallbackService}.
     *
     * @param workspace      the per-conversation blob store (T111)
     * @param agentId        active agent id (path-isolated); {@code null} → no-op mode
     * @param conversationId active conversation id (path-isolated); {@code null} → no-op mode
     */
    public TurCustomToolWorkspaceHelper(TurAgentWorkspace workspace, String agentId, String conversationId) {
        this.workspace = workspace;
        this.agentId = agentId;
        this.conversationId = conversationId;
    }

    /**
     * Stores (or overwrites) a UTF-8 string blob at {@code key}. Convenience
     * overload mirroring the {@code workspace.put(key, value)} signature from
     * the spec — the value is encoded as {@code text/plain; charset=utf-8}.
     */
    public void put(String key, String value) {
        put(key, value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8),
                "text/plain; charset=utf-8");
    }

    /**
     * Stores (or overwrites) a byte blob at {@code key} with an explicit
     * content type ({@code null} → guessed from the key). No-op when invoked
     * outside an active conversation.
     */
    public void put(String key, byte[] content, String contentType) {
        if (isNoOp("put", key)) {
            return;
        }
        workspace.put(agentId, conversationId, key, content, contentType);
        log.info("[workspace.put] conv={} key='{}' wrote {} bytes",
                conversationId, key, content == null ? 0 : content.length);
    }

    /**
     * Reads the raw bytes stored at {@code key}, or {@code null} when the key
     * does not exist, storage is disabled, or the helper is in no-op mode.
     */
    public byte[] get(String key) {
        if (isNoOp("get", key)) {
            return null;
        }
        Optional<byte[]> bytes = workspace.get(agentId, conversationId, key);
        return bytes.orElse(null);
    }

    /**
     * Reads the blob at {@code key} as a UTF-8 string, or {@code null} when
     * absent. Convenience for the common text-artifact case (markdown drafts,
     * JSON snapshots).
     */
    public String getText(String key) {
        byte[] bytes = get(key);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Lists every artifact whose key starts with {@code prefix} (recursive,
     * scoped to this conversation). A {@code null}/blank prefix lists the whole
     * workspace. Returns an empty list outside an active conversation.
     */
    public List<WorkspaceEntry> list(String prefix) {
        if (agentId == null || conversationId == null) {
            log.warn("[workspace.list] NO-OP — agentId={} conversationId={} (Custom Tool invoked outside an "
                    + "active chat session)", agentId, conversationId);
            return Collections.emptyList();
        }
        return workspace.list(agentId, conversationId, prefix);
    }

    /** Lists the whole workspace ({@code list(null)}). */
    public List<WorkspaceEntry> list() {
        return list(null);
    }

    /** Deletes the blob at {@code key}. No-op when absent or outside a conversation. */
    public void delete(String key) {
        if (isNoOp("delete", key)) {
            return;
        }
        workspace.delete(agentId, conversationId, key);
        log.info("[workspace.delete] conv={} key='{}'", conversationId, key);
    }

    /**
     * Builds an HMAC-signed, time-limited download URL for {@code key} (served
     * through {@code /api/v2/workspace/file}). Returns {@code null} outside an
     * active conversation. The LLM can embed the returned URL in its reply so
     * the user downloads the artifact without the bytes touching the prompt.
     */
    public String url(String key) {
        if (isNoOp("url", key)) {
            return null;
        }
        return workspace.signedUrl(agentId, conversationId, key);
    }

    /** The conversation this helper writes to. May be {@code null} (no-op mode). */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * Guards the mutate/read-single operations: logs and returns {@code true}
     * when the helper has no tenant context (so callers short-circuit), or when
     * the key is null/blank (defensive against a buggy script).
     */
    private boolean isNoOp(String op, String key) {
        if (agentId == null || conversationId == null) {
            log.warn("[workspace.{}] NO-OP — agentId={} conversationId={} (Custom Tool invoked outside an "
                    + "active chat session, or missing tenant context in ToolContext)",
                    op, agentId, conversationId);
            return true;
        }
        if (key == null || key.isBlank()) {
            log.warn("[workspace.{}] NO-OP — null/blank key (conv={})", op, conversationId);
            return true;
        }
        return false;
    }
}
