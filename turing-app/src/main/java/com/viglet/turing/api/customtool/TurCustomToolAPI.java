/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.customtool;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.adapter.customtool.TurCustomToolReader;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService.PreviewResult;
import com.viglet.turing.genai.tool.TurCustomToolDescriptorService;
import com.viglet.turing.genai.tool.TurCustomToolDescriptorService.ToolEditorDescriptor;
import com.viglet.turing.genai.tool.TurCustomToolDraftRegistry;
import com.viglet.turing.genai.tool.TurCustomToolDraftRegistry.DraftEntry;
import com.viglet.turing.genai.tool.TurCustomToolDraftRegistry.PutResult;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.viglet.turing.persistence.dto.customtool.TurCustomToolDto;
import com.viglet.turing.persistence.mapper.customtool.TurCustomToolMapper;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import groovy.lang.GroovyShell;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@RestController
@RequestMapping("/api/custom-tool")
@Tag(name = "Custom Tool", description = "Custom Tool API")
public class TurCustomToolAPI {
    private final TurCustomToolRepository repository;
    private final TurCustomToolMapper mapper;
    private final TurCustomToolReader reader;
    private final TurCustomToolCallbackService callbackService;
    private final TurCustomToolDescriptorService descriptorService;
    private final TurCustomToolDraftRegistry draftRegistry;

    public TurCustomToolAPI(TurCustomToolRepository repository,
            TurCustomToolMapper mapper,
            TurCustomToolReader reader,
            TurCustomToolCallbackService callbackService,
            TurCustomToolDescriptorService descriptorService,
            TurCustomToolDraftRegistry draftRegistry) {
        this.repository = repository;
        this.mapper = mapper;
        this.reader = reader;
        this.callbackService = callbackService;
        this.descriptorService = descriptorService;
        this.draftRegistry = draftRegistry;
    }

    @Operation(summary = "Custom Tool List")
    @GetMapping
    public List<TurCustomToolDto> list() {
        return mapper.toDtoList(repository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "Custom Tool structure")
    @GetMapping("/structure")
    public TurCustomToolDto structure() {
        return mapper.toDto(new TurCustomTool());
    }

    /**
     * Editor descriptor — enumerates every binding the runtime injects into a
     * Custom Tool Groovy script ({@code http}, {@code slots}, {@code turingSearch},
     * {@code code}, and the {@code args} global) along with method signatures
     * and one-line descriptions. The admin's CodeMirror editor consumes this
     * payload to power auto-complete and signature hints (T40); the shape is
     * editor-agnostic so a future Monaco / LSP path can reuse the same
     * endpoint.
     */
    @Operation(summary = "Editor descriptor for Custom Tool Groovy auto-complete")
    @GetMapping("/descriptor")
    public ToolEditorDescriptor descriptor() {
        return descriptorService.get();
    }

    @Operation(summary = "Show a Custom Tool")
    @GetMapping("/{id}")
    public TurCustomToolDto get(@PathVariable String id) {
        return mapper.toDto(repository.findById(id).orElse(new TurCustomTool()));
    }

    @Operation(summary = "Update a Custom Tool")
    @PutMapping("/{id}")
    public TurCustomToolDto update(@PathVariable String id, @RequestBody TurCustomToolDto dto) {
        TurCustomTool source = mapper.toEntity(dto);
        return repository.findById(id).map(existing -> {
            mapper.updateEntity(source, existing);
            repository.save(existing);
            return mapper.toDto(existing);
        }).orElse(new TurCustomToolDto());
    }

    @Transactional
    @Operation(summary = "Delete a Custom Tool")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        repository.delete(id);
        return true;
    }

    @Operation(summary = "Create a Custom Tool")
    @PostMapping
    public TurCustomToolDto add(@RequestBody TurCustomToolDto dto) {
        TurCustomTool entity = mapper.toEntity(dto);
        repository.save(entity);
        return mapper.toDto(entity);
    }

    /**
     * Groovy 4's ANTLR-based parser writes "unknown recognition error type: ..."
     * messages straight to {@link System#out}/{@link System#err} for some
     * unrecognizable tokens — outside the regular {@code ErrorCollector}
     * pipeline. We synchronize globally on this monitor while the streams
     * are redirected so a concurrent log line from elsewhere doesn't get
     * swallowed.
     */
    private static final Object PARSE_OUTPUT_LOCK = new Object();

    @Operation(summary = "Validate a Groovy script for syntax / compile errors")
    @PostMapping("/validate")
    @SuppressWarnings("java:S106") // not logging: capture System.out/err to restore them
    // after temporarily redirecting both to a sink so the Groovy parser's stray
    // stdout/stderr chatter doesn't leak into the application log.
    public ValidateScriptResponse validate(@RequestBody ValidateScriptRequest request) {
        String script = request.script() == null ? "" : request.script();
        synchronized (PARSE_OUTPUT_LOCK) {
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            PrintStream sink = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
            System.setOut(sink);
            System.setErr(sink);
            try {
                // Force a full parse — we only care about compile-time issues, no execution.
                new GroovyShell().getClassLoader().parseClass(script, "TurCustomTool_validate.groovy");
                return new ValidateScriptResponse(true, null, null, null);
            } catch (MultipleCompilationErrorsException e) {
                // Pull the first SyntaxException so we can surface line/column to the editor.
                return e.getErrorCollector().getErrors().stream()
                        .filter(SyntaxErrorMessage.class::isInstance)
                        .map(SyntaxErrorMessage.class::cast)
                        .findFirst()
                        .map(SyntaxErrorMessage::getCause)
                        .map(this::toErrorResponse)
                        .orElseGet(() -> new ValidateScriptResponse(false, e.getMessage(), null, null));
            } catch (Exception e) {
                return new ValidateScriptResponse(false, e.getMessage(), null, null);
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
                sink.close();
            }
        }
    }

    private ValidateScriptResponse toErrorResponse(SyntaxException sx) {
        return new ValidateScriptResponse(false, sx.getMessage(),
                sx.getStartLine() > 0 ? sx.getStartLine() : null,
                sx.getStartColumn() > 0 ? sx.getStartColumn() : null);
    }

    public record ValidateScriptRequest(String script) {
    }

    public record ValidateScriptResponse(boolean valid, String error, Integer line, Integer column) {
    }

    /**
     * Hot-reload preview: re-runs the persisted tool against caller-supplied
     * args without touching any chat conversation. The standard bindings
     * ({@code args}, {@code http}, {@code turingSearch}, {@code slots}) are
     * all active — {@code slots.set} is a no-op since there is no live
     * conversation, but everything else (HTTP calls, vector search) executes
     * for real so the admin can verify behavior with real data.
     *
     * <p>The cached compiled script in {@link TurCustomToolCallbackService}
     * is invalidated automatically on each call when the source has changed
     * (hash-based), so saving a new version in the editor + clicking "Test"
     * picks up the fresh code without a server restart — that's the
     * hot-reload story.
     *
     * @param id   id of the persisted custom tool to execute
     * @param body request envelope with the {@code argsJson} the LLM would
     *             have sent (e.g. {@code {"query":"finanças CFO"}})
     */
    @Operation(summary = "Execute a Custom Tool against caller-supplied args (admin preview)")
    @PostMapping("/{id}/execute")
    public ExecuteToolResponse execute(@PathVariable String id,
            @RequestBody ExecuteToolRequest body) {
        return reader.findById(id)
                .map(tool -> {
                    PreviewResult preview = callbackService.executeForPreview(tool,
                            body == null || body.argsJson() == null ? "{}" : body.argsJson());
                    return new ExecuteToolResponse(preview.ok(), preview.result(),
                            preview.error(), preview.elapsedMs());
                })
                .orElse(new ExecuteToolResponse(false, null,
                        "Custom tool '" + id + "' not found.", 0L));
    }

    public record ExecuteToolRequest(String argsJson) {
    }

    public record ExecuteToolResponse(boolean ok, String result, String error, long elapsedMs) {
    }

    /* ─────────────────────────  T41 live-preview draft API  ─────────────────────────
     *
     * Admin pushes the in-editor Groovy as a "draft" — the same admin's chat
     * conversations will then execute the draft for that tool, while every
     * other session keeps seeing the persisted version. Lets authors iterate
     * against real LLM + RAG without persisting half-baked scripts.
     *
     * The "WebSocket" in T41's spec is loose — this codebase has no
     * spring-boot-starter-websocket on the classpath and standardises on
     * REST + SSE (see TurChatSlotEventBus / TurSNSiteGenAiAPI streamSlots).
     * One-way client→server push doesn't need duplex, so plain REST PUT/DELETE
     * matches the convention while delivering the same UX. The chat side
     * still uses the existing SSE for token streaming.
     */

    /**
     * Stores or replaces the calling admin's draft Groovy source for tool
     * {@code id}. Compiled synchronously; a compile error returns 200 with
     * {@code success=false} and the offending line/column so the editor can
     * highlight in place. Subsequent chat invocations by the same admin run
     * the draft until {@link #deleteDraft(String)} or the registry TTL
     * expires the entry.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Push live-preview draft Groovy for this admin (T41)")
    @PutMapping("/{id}/draft")
    public DraftPutResponse putDraft(@PathVariable String id, @RequestBody DraftPutRequest body) {
        String username = currentUsername();
        if (username == null) {
            return new DraftPutResponse(false, "Anonymous sessions cannot push drafts.", null, null, null);
        }
        if (reader.findById(id).isEmpty()) {
            return new DraftPutResponse(false, "Custom tool '" + id + "' not found.", null, null, null);
        }
        PutResult put = draftRegistry.put(username, id, body == null ? "" : body.groovyScript());
        if (!put.success()) {
            return new DraftPutResponse(false, put.error(), put.line(), put.column(), null);
        }
        return new DraftPutResponse(true, null, null, null, put.entry().createdAt().toString());
    }

    /**
     * Clears the calling admin's draft for tool {@code id} (if any). Chat
     * sessions immediately revert to the persisted tool source on the next
     * invocation. Idempotent — clearing a non-existent draft returns
     * {@code cleared=false} without error.
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Clear this admin's live-preview draft (T41)")
    @DeleteMapping("/{id}/draft")
    public DraftClearResponse deleteDraft(@PathVariable String id) {
        String username = currentUsername();
        if (username == null) {
            return new DraftClearResponse(false);
        }
        return new DraftClearResponse(draftRegistry.clear(username, id));
    }

    /**
     * Reports whether the calling admin currently has an active draft for
     * tool {@code id}. Used by the editor to show a "draft active" banner
     * and to restore the in-flight source on page reload (the draft survives
     * a navigation, the unsaved-form-state in React does not).
     *
     * @since 2026.3.1
     */
    @Operation(summary = "Fetch this admin's live-preview draft status (T41)")
    @GetMapping("/{id}/draft")
    public DraftStatusResponse getDraft(@PathVariable String id) {
        String username = currentUsername();
        if (username == null) {
            return new DraftStatusResponse(false, null, null, null);
        }
        return draftRegistry.find(username, id)
                .map((DraftEntry e) -> new DraftStatusResponse(true, e.groovySource(),
                        e.createdAt().toString(), e.lastTouched().toString()))
                .orElseGet(() -> new DraftStatusResponse(false, null, null, null));
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name;
    }

    public record DraftPutRequest(String groovyScript) {
    }

    public record DraftPutResponse(boolean success, String error, Integer line, Integer column,
            String createdAt) {
    }

    public record DraftClearResponse(boolean cleared) {
    }

    public record DraftStatusResponse(boolean active, String groovyScript, String createdAt,
            String lastTouched) {
    }
}
