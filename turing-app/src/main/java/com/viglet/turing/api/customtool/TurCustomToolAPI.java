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

import com.viglet.turing.domain.customtool.TurCustomToolRepositoryPort;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService;
import com.viglet.turing.genai.tool.TurCustomToolCallbackService.PreviewResult;
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
    private final TurCustomToolRepositoryPort repositoryPort;
    private final TurCustomToolCallbackService callbackService;

    public TurCustomToolAPI(TurCustomToolRepository repository,
            TurCustomToolMapper mapper,
            TurCustomToolRepositoryPort repositoryPort,
            TurCustomToolCallbackService callbackService) {
        this.repository = repository;
        this.mapper = mapper;
        this.repositoryPort = repositoryPort;
        this.callbackService = callbackService;
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
        return repositoryPort.findById(id)
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
}
