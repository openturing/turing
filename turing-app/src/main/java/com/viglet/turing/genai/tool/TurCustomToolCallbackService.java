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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.domain.customtool.TurCustomToolDomain;
import com.viglet.turing.domain.customtool.TurCustomToolRepositoryPort;
import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import org.springframework.context.annotation.Lazy;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds Spring AI {@link ToolCallback} instances backed by user-defined
 * Groovy scripts. Each {@link TurCustomToolDomain} becomes one callback
 * whose {@code call(String)} parses the LLM JSON arguments, binds them
 * into the Groovy script, evaluates, and returns the script result as a
 * string.
 *
 * <p>Compiled scripts are cached per tool id and invalidated when the
 * source changes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@Slf4j
@Service
public class TurCustomToolCallbackService {

    /**
     * Key under which {@link com.viglet.turing.genai.TurAgentChatExecutor}
     * publishes the active conversation id into the Spring AI
     * {@code ToolContext}. Custom Tool Groovy scripts read it via the
     * {@code slots} binding (a {@link TurCustomToolSlotHelper} pre-bound to
     * this id) so they can write/read chat-flow variables on the live
     * conversation.
     */
    public static final String TOOL_CONTEXT_CONVERSATION_ID = "turing.conversationId";

    /**
     * Key under which {@link com.viglet.turing.genai.TurAgentChatExecutor}
     * publishes the active agent's {@code pythonRequirements}
     * {@code requirements.txt}-style addendum. The
     * {@link TurCustomToolCodeHelper} pre-bound to each Custom Tool
     * invocation carries this value into
     * {@link TurCodeInterpreterToolService#executePythonWithExtraRequirements}
     * — the sandbox installs the UNION of global + agent before running
     * the Python script. Null/blank means "no addendum, global env only."
     */
    public static final String TOOL_CONTEXT_AGENT_PYTHON_REQUIREMENTS = "turing.agentPythonRequirements";

    /**
     * Key under which {@link com.viglet.turing.genai.TurAgentChatExecutor}
     * publishes the active agent's id. Combined with
     * {@link #TOOL_CONTEXT_CONVERSATION_ID}, it drives the tenant-isolated
     * session-dir layout in {@link TurCodeInterpreterToolService}:
     * {@code tenants/{agentId}/{conversationId}/YYYY-MM-DD/{sid}/}.
     *
     * <p>Path-level isolation only — not a runtime sandbox boundary.
     * See {@link TurCustomToolCodeHelper} JSDoc for the full security
     * caveat.
     */
    public static final String TOOL_CONTEXT_AGENT_ID = "turing.agentId";

    /**
     * T24b / §III.2 — key under which the SN site chat API publishes the
     * active query locale (as a {@link java.util.Locale#toLanguageTag}
     * IETF BCP 47 tag, e.g. {@code "pt-BR"}). Consumed by
     * {@code TurRagSearchToolService} when the agent's
     * {@code ragBm25Source} is {@code SE_INSTANCE}: the per-locale RAG
     * BM25 core for this tag is the one the tool routes the keyword
     * pass to. Empty / missing → falls back to the EMBEDDED path so the
     * tool still returns something (degraded — no per-locale tokenization).
     *
     * <p>Only populated on the SN site chat API path. The standalone
     * agent-chat endpoint doesn't set it, by design — RAG isn't a
     * supported tool outside SN site context (see the T19/T24
     * architectural-reposition note in IMPROVEMENTS.md).
     *
     * @since 2026.2.7
     */
    public static final String TOOL_CONTEXT_LOCALE = "turing.locale";

    /**
     * T41 — key under which the chat executor publishes the authenticated
     * user's username (resolved from {@code SecurityContextHolder} on the
     * HTTP thread). Consumed by {@link #executeGroovy(TurCustomToolDomain,
     * String, ToolContext)} to look up an active Custom Tool draft for the
     * caller, enabling live unsaved-script preview without affecting other
     * sessions.
     *
     * @since 2026.3.1
     */
    public static final String TOOL_CONTEXT_USERNAME = "turing.username";

    /**
     * T109 — key under which {@link TurCustomToolAgentHelper} increments the
     * current {@code agent.invoke(...)} recursion depth on each nested call.
     * The entry tool runs at depth 0; a sub-call running inside a tool
     * spawned by {@code agent.invoke(...)} sees depth 1; etc. Capped at
     * {@link TurCustomToolAgentHelper#DEFAULT_DEPTH_CAP} (3). Absent
     * → treated as 0.
     *
     * @since 2026.3.1
     */
    public static final String TOOL_CONTEXT_AGENT_INVOKE_DEPTH = "turing.agentInvokeDepth";

    /**
     * T292 — key under which the chat executor publishes a
     * {@link com.viglet.turing.genai.rag.TurRagSourceCollector} so the
     * {@code search_knowledge_base} tool can record the provenance
     * ({@code sourceId} / {@code title} / {@code url} / {@code chunkIndex} /
     * {@code score}) of every retrieved chunk. The streaming dispatcher drains
     * the collector after the tool-execution loop and emits it as the
     * structured {@code sources[]} SSE event. Absent → the tool simply skips
     * provenance capture (legacy callers / unit tests).
     *
     * @since 2026.3.1
     */
    public static final String TOOL_CONTEXT_RAG_SOURCES = "turing.ragSources";

    private final TurCustomToolRepositoryPort repositoryPort;
    private final TurCustomToolSearchHelper searchHelper;
    private final TurChatFlowStateRepository stateRepository;
    private final com.viglet.turing.service.chatslots.TurChatSlotEventBus slotEventBus;
    private final com.viglet.turing.service.chatslots.TurChatSlotAuditService slotAuditService;
    private final TurCodeInterpreterToolService codeInterpreterToolService;
    private final TurCustomToolDraftRegistry draftRegistry;
    private final TurAgentChatExecutor agentChatExecutor;
    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmRepository;
    private final com.viglet.turing.genai.workspace.TurAgentWorkspace agentWorkspace;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CachedScript> scriptCache = new ConcurrentHashMap<>();

    public TurCustomToolCallbackService(TurCustomToolRepositoryPort repositoryPort,
            TurCustomToolSearchHelper searchHelper,
            TurChatFlowStateRepository stateRepository,
            com.viglet.turing.service.chatslots.TurChatSlotEventBus slotEventBus,
            com.viglet.turing.service.chatslots.TurChatSlotAuditService slotAuditService,
            TurCodeInterpreterToolService codeInterpreterToolService,
            TurCustomToolDraftRegistry draftRegistry,
            // @Lazy breaks the cycle: TurAgentChatExecutor → TurChatToolResolver
            // → TurCustomToolCallbackService → TurAgentChatExecutor (T109). Spring
            // resolves the proxy lazily on first use, by which point both beans
            // exist.
            @Lazy TurAgentChatExecutor agentChatExecutor,
            TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmRepository,
            com.viglet.turing.genai.workspace.TurAgentWorkspace agentWorkspace) {
        this.repositoryPort = repositoryPort;
        this.searchHelper = searchHelper;
        this.stateRepository = stateRepository;
        this.slotEventBus = slotEventBus;
        this.slotAuditService = slotAuditService;
        this.codeInterpreterToolService = codeInterpreterToolService;
        this.draftRegistry = draftRegistry;
        this.agentChatExecutor = agentChatExecutor;
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
        this.agentWorkspace = agentWorkspace;
    }

    /**
     * Get tool callbacks only for the specified custom tools (filtered by
     * agent configuration). The argument keeps its JPA-entity shape so
     * callers that already hold {@code agent.getCustomTools()} need not
     * project — internally we collapse the set to ids and resolve through
     * the port.
     */
    public ToolCallback[] getToolCallbacks(Set<TurCustomTool> customTools) {
        if (customTools == null || customTools.isEmpty()) {
            return new ToolCallback[0];
        }
        Set<String> allowedIds = customTools.stream()
                .map(TurCustomTool::getId)
                .collect(Collectors.toSet());

        return repositoryPort.findAllEnabled().stream()
                .filter(t -> allowedIds.contains(t.id()))
                .map(this::buildCallback)
                .toArray(ToolCallback[]::new);
    }

    /** All enabled custom tool callbacks across the platform. */
    public ToolCallback[] getToolCallbacks() {
        return repositoryPort.findAllEnabled().stream()
                .map(this::buildCallback)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback buildCallback(TurCustomToolDomain tool) {
        return new GroovyToolCallback(tool);
    }

    /* ---------- JSON Schema construction ---------- */

    private String buildInputSchema(TurCustomToolDomain tool) {
        List<ParameterDef> params = readParameters(tool);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ParameterDef p : params) {
            properties.put(p.name(), Map.of("type", normalizeType(p.type())));
            required.add(p.name());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            log.warn("[CustomTool] Failed to build input schema for tool '{}'", tool.title(), e);
            return "{\"type\":\"object\",\"properties\":{}}";
        }
    }

    private List<ParameterDef> readParameters(TurCustomToolDomain tool) {
        if (tool.parametersJson() == null || tool.parametersJson().isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tool.parametersJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[CustomTool] Could not parse parametersJson for '{}': {}", tool.title(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "string";
        }
        return switch (type.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "number", "double", "float" -> "number";
            case "boolean", "bool" -> "boolean";
            default -> "string";
        };
    }

    /* ---------- Groovy execution ---------- */

    @SuppressWarnings("unchecked")
    private CachedScript scriptFor(TurCustomToolDomain tool) {
        String id = tool.id();
        int hash = tool.groovyScript() == null ? 0 : tool.groovyScript().hashCode();
        CachedScript cached = scriptCache.get(id);
        if (cached != null && cached.scriptHash == hash) {
            return cached;
        }
        GroovyShell shell = new GroovyShell();
        String source = sanitizeGroovySource(tool.groovyScript() == null ? "" : tool.groovyScript());
        Class<? extends Script> clazz = (Class<? extends Script>) shell.getClassLoader().parseClass(
                source,
                "TurCustomTool_" + id + ".groovy");
        CachedScript fresh = new CachedScript(clazz, hash);
        scriptCache.put(id, fresh);
        return fresh;
    }

    /**
     * Defangs source-level tokens that confuse the Groovy ANTLR4 lexer
     * inside triple-quote heredocs ({@code '''...'''}). Custom tools
     * routinely embed Python source as a heredoc — and Python regex
     * patterns like {@code r"[\U0001F000-\U0001FFFF]"} contain {@code \U}
     * sequences. The Groovy lexer mis-handles {@code \U} inside heredocs:
     * a subsequent {@code '''} opener is then reported as
     * {@code "Unexpected character: '\''"} at the heredoc's first column.
     *
     * <p>We swap a bare {@code \U} (not already escaped by a preceding
     * {@code \}) for {@code \\U}. Inside a Groovy triple-single-quote
     * heredoc, {@code \\U} is the escape for a single literal backslash
     * followed by {@code U} — so the runtime string still contains
     * exactly {@code \U}, which is what the Python sandbox needs. The
     * lexer is happy because the offending token never appears.
     *
     * <p>Reproduced by {@link com.viglet.turing.genai.tool.TurCustomToolGroovyParserBugTest}.
     */
    static String sanitizeGroovySource(String src) {
        if (src == null || src.isEmpty() || src.indexOf("\\U") < 0) {
            return src == null ? "" : src;
        }
        // (?<!\\) — \U not preceded by another backslash (so authors who
        // already wrote \\U don't get doubled to \\\U).
        return src.replaceAll("(?<!\\\\)\\\\U", "\\\\\\\\U");
    }

    String executeGroovy(TurCustomToolDomain tool, String toolInput) throws ReflectiveOperationException {
        return executeGroovy(tool, toolInput, null);
    }

    /**
     * Admin-facing preview hook: runs the tool's Groovy script with the
     * supplied args JSON, exactly as the chat executor would, but with NO
     * conversation context (slot writes become no-ops) and NO rich-return
     * fence wrapping. Returns the raw value the script returned so the admin
     * editor can show "this is what the LLM would see" without having to
     * spin up a chat to test a tweak.
     *
     * <p>Exceptions are caught and serialized into the response so a buggy
     * script doesn't bubble a 500 — the UI can show the error string and
     * the operator iterates.
     *
     * @since 2026.2.7
     */
    public PreviewResult executeForPreview(TurCustomToolDomain tool, String argsJson) {
        long start = System.currentTimeMillis();
        try {
            String result = executeGroovy(tool, argsJson, null);
            long elapsed = System.currentTimeMillis() - start;
            log.info("[CustomTool/preview] '{}' completed in {}ms ({} chars)",
                    tool.title(), elapsed, result == null ? 0 : result.length());
            return new PreviewResult(true, result, null, elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("[CustomTool/preview] '{}' failed in {}ms: {}",
                    tool.title(), elapsed, e.getMessage());
            return new PreviewResult(false, null, e.getMessage(), elapsed);
        }
    }

    /**
     * Plain result envelope for {@link #executeForPreview(TurCustomToolDomain, String)}.
     * Either {@code result} or {@code error} is populated (the other is
     * {@code null}); {@code elapsedMs} is wall-clock time including parse
     * + execute.
     */
    public record PreviewResult(boolean ok, String result, String error, long elapsedMs) {
    }

    /**
     * Two-argument variant invoked by {@code GroovyToolCallback.call(...,
     * ToolContext)} so scripts can write back into the live conversation's
     * slot map. The conversation id arrives via the context key
     * {@link #TOOL_CONTEXT_CONVERSATION_ID}; when absent the {@code slots}
     * binding is created in no-op mode so callers can still test scripts
     * outside an active session.
     */
    String executeGroovy(TurCustomToolDomain tool, String toolInput, ToolContext toolContext)
            throws ReflectiveOperationException {
        Map<String, Object> args = parseArgs(toolInput);
        // T41 live-preview overlay — if the caller has an active draft for
        // this tool in TurCustomToolDraftRegistry, execute the draft source
        // instead of the persisted version. Per-admin scope: visitors and
        // other admins still see the persisted tool. Falls through to the
        // normal scriptFor(...) cache when no draft is active.
        String username = resolveUsername(toolContext);
        Class<? extends Script> scriptClass;
        var draft = draftRegistry.find(username, tool.id());
        if (draft.isPresent()) {
            scriptClass = draft.get().scriptClass();
            log.info("[CustomTool/draft] EXECUTING DRAFT username='{}' toolId='{}' (T41 live preview)",
                    username, tool.id());
        } else {
            scriptClass = scriptFor(tool).scriptClass;
        }
        Binding binding = new Binding(new HashMap<>(args));
        // Standard bindings every script receives:
        //   args         — the parsed LLM-supplied arguments as a Map (also each
        //                  key is auto-bound as a top-level variable, e.g.
        //                  `query` for {"query": "..."}).
        //   http         — generic HTTP client (postJson, getJson) for tools
        //                  that hit arbitrary external APIs.
        //   turingSearch — high-level abstraction over the Turing search
        //                  surfaces (ann, sn) so scripts don't have to know
        //                  about VectorStore / SearchRequest plumbing.
        //   slots        — conversation-scoped getter/setter for chat-flow
        //                  variables. set(name, value) propagates to the
        //                  React portal on its next polling tick — that's
        //                  how a tool feeds dynamic data into UI components
        //                  like the "Programas recomendados" card grid.
        //   workspace    — per-conversation blob store (T112). put/get/list/
        //                  delete/url for drafts, partial results, and
        //                  intermediate artifacts; url(key) yields a signed
        //                  download URL the LLM can hand the user.
        binding.setVariable("args", args);
        // Slot helper is constructed first so the http helper can take it as
        // a credential source (T39 reserved __ prefix → auto Authorization
        // header). When the conversation id is null the slot helper operates
        // in no-op mode and auto-auth degrades to "no auth," which is what
        // unit-test invocations rely on.
        TurCustomToolSlotHelper slotHelper = new TurCustomToolSlotHelper(
                resolveConversationId(toolContext), stateRepository, slotEventBus,
                slotAuditService, tool == null ? null : tool.title());
        binding.setVariable("http", new TurCustomToolHttpHelper(slotHelper));
        binding.setVariable("turingSearch", searchHelper);
        binding.setVariable("slots", slotHelper);
        // `code` — sandboxed Python execution via TurCodeInterpreterToolService.
        // Lets a Custom Tool offload heavy work (PDF rendering with reportlab,
        // matplotlib charts, data transforms) to the platform's Python runtime
        // without re-implementing process management or file-serving plumbing.
        //
        // The helper carries the active agent's `pythonRequirements` addendum
        // (when present in toolContext) so the dep service installs the UNION
        // of global + agent before the Python subprocess starts. This is how
        // an analytics agent declaring `pandas` gets pandas alongside the
        // baseline reportlab from Global Settings — no operator handoff
        // between environments.
        String agentPythonRequirements = resolveAgentPythonRequirements(toolContext);
        String tenantAgentId = resolveAgentId(toolContext);
        String tenantConversationId = resolveConversationId(toolContext);
        binding.setVariable("code",
                new TurCustomToolCodeHelper(codeInterpreterToolService,
                        agentPythonRequirements, tenantAgentId, tenantConversationId));
        // T109 — cross-agent delegation. The helper carries the current
        // recursion depth (incremented by 1 against the parent context's
        // value) so a nested agent.invoke(...) can short-circuit at
        // DEFAULT_DEPTH_CAP without blowing the stack. The parent
        // conversation id flows through so child invocations carry the
        // parent linkage in their first user message (parsed later by T110).
        int parentDepth = resolveAgentInvokeDepth(toolContext);
        binding.setVariable("agent",
                new TurCustomToolAgentHelper(agentChatExecutor, agentRepository,
                        llmRepository, tenantConversationId, parentDepth + 1));
        // T112 — `workspace`: per-conversation blob store (T111) for drafts,
        // partial results, and intermediate artifacts. Pre-bound to the active
        // tenant (agentId + conversationId); writes land under
        // tenants/{agentId}/{conversationId}/workspace/<key>. workspace.url(key)
        // returns an HMAC-signed download URL the LLM can hand the user without
        // the bytes touching the prompt. No-op mode when tenant context absent.
        binding.setVariable("workspace",
                new TurCustomToolWorkspaceHelper(agentWorkspace, tenantAgentId, tenantConversationId));
        Script script = scriptClass.getDeclaredConstructor().newInstance();
        script.setBinding(binding);
        Object result = script.run();
        return result == null ? "" : result.toString();
    }

    /**
     * Extracts the authenticated user's username published by
     * {@code TurAgentChatExecutor.buildChatOptions} via
     * {@link #TOOL_CONTEXT_USERNAME}. Drives the T41 draft-overlay lookup
     * in {@link TurCustomToolDraftRegistry}. Returns {@code null} when no
     * context or no username is available (e.g. anonymous chat session,
     * unit-test invocation) — the registry treats null as "no draft," so
     * the regular persisted script runs.
     */
    private static String resolveUsername(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) {
            return null;
        }
        Object value = ctx.get(TOOL_CONTEXT_USERNAME);
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /**
     * Extracts the conversation id published by {@code TurAgentChatExecutor}
     * into the tool context. Returns {@code null} when the context is null
     * (e.g. unit-test invocation) or the key is missing — the slot helper
     * handles that defensively with no-op writes.
     */
    private static String resolveConversationId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) {
            return null;
        }
        Object value = ctx.get(TOOL_CONTEXT_CONVERSATION_ID);
        return value == null ? null : value.toString();
    }

    /**
     * Extracts the active agent's Python {@code requirements.txt} addendum
     * (when {@code TurAgentChatExecutor} published it via {@link
     * #TOOL_CONTEXT_AGENT_PYTHON_REQUIREMENTS}). Returns null when the
     * context is absent (unit-test invocation), the key is missing, or
     * the value is blank.
     */
    private static String resolveAgentPythonRequirements(ToolContext toolContext) {
        if (toolContext == null) return null;
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) return null;
        Object value = ctx.get(TOOL_CONTEXT_AGENT_PYTHON_REQUIREMENTS);
        if (value == null) return null;
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /**
     * Extracts the active agent's id published via
     * {@link #TOOL_CONTEXT_AGENT_ID}. Drives the tenant-isolated path
     * layout in {@link TurCodeInterpreterToolService}; null when no
     * agent context is available (e.g. unit tests).
     */
    private static String resolveAgentId(ToolContext toolContext) {
        if (toolContext == null) return null;
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) return null;
        Object value = ctx.get(TOOL_CONTEXT_AGENT_ID);
        if (value == null) return null;
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    /**
     * T109 — extracts the current {@code agent.invoke(...)} recursion depth
     * from the Spring AI {@code ToolContext} (published via
     * {@link #TOOL_CONTEXT_AGENT_INVOKE_DEPTH}). Returns 0 when the context
     * is absent, the key is missing, or the value cannot be parsed —
     * matches the "entry tool runs at depth 0" semantics documented on
     * {@link TurCustomToolAgentHelper}.
     */
    private static int resolveAgentInvokeDepth(ToolContext toolContext) {
        if (toolContext == null) return 0;
        Map<String, Object> ctx = toolContext.getContext();
        if (ctx == null) return 0;
        Object value = ctx.get(TOOL_CONTEXT_AGENT_INVOKE_DEPTH);
        if (value == null) return 0;
        if (value instanceof Number n) return Math.max(0, n.intValue());
        try {
            return Math.max(0, Integer.parseInt(value.toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Object> parseArgs(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(toolInput, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("[CustomTool] Could not parse toolInput as JSON: {}", e.getMessage());
            return Map.of("input", toolInput);
        }
    }

    /* ---------- Inner types ---------- */

    public record ParameterDef(String name, String type) {
    }

    private record CachedScript(Class<? extends Script> scriptClass, int scriptHash) {
    }

    /**
     * Strategy for the formats the chat UI knows how to render specially.
     * For each, we (a) wrap the Groovy output in a markdown code fence (or
     * leave it bare for {@code MARKDOWN} — markdown IS the default chat
     * format), and (b) augment the tool description with an instruction
     * telling the LLM to relay the output VERBATIM so the wrapper survives.
     */
    private enum RichReturn {
        /** Iframe sandbox renderer ({@code SandboxPlayer}). */
        HTML("html", """


                IMPORTANT: This tool returns HTML wrapped in a ```html fenced code block. \
                When the user asks for the result, return the tool's output VERBATIM, \
                INCLUDING the opening and closing ``` fences. Do NOT translate, \
                summarize, or rephrase. The chat UI renders it in a sandboxed iframe \
                — the fence is what triggers that rendering."""),
        /** Default markdown renderer (react-markdown + remark-gfm). No fence — markdown is the native format. */
        MARKDOWN(null, """


                IMPORTANT: This tool returns markdown-formatted text (headings, lists, \
                tables, links, etc.). Return the output VERBATIM. Do NOT translate, \
                summarize, or change the formatting. The chat UI renders markdown natively."""),
        /** Highlighted code block via rehype-highlight. */
        JSON("json", """


                IMPORTANT: This tool returns JSON wrapped in a ```json fenced code block. \
                Return the tool's output VERBATIM, INCLUDING the opening and closing \
                ``` fences. Do NOT translate, summarize, or rephrase. The chat UI \
                renders it as a highlighted JSON block.""");

        /** Markdown info-string for the fence; null = no fence. */
        final String fenceLanguage;
        final String descriptionHint;

        RichReturn(String fenceLanguage, String descriptionHint) {
            this.fenceLanguage = fenceLanguage;
            this.descriptionHint = descriptionHint;
        }

        String wrap(String result) {
            return fenceLanguage == null
                    ? result
                    : "```" + fenceLanguage + "\n" + result + "\n```";
        }

        static RichReturn from(String returnType) {
            if (returnType == null) return null;
            return switch (returnType.toLowerCase()) {
                case "html" -> HTML;
                case "markdown" -> MARKDOWN;
                case "json" -> JSON;
                default -> null;
            };
        }
    }

    /**
     * Spring AI ToolCallback adapter for a single TurCustomToolDomain.
     * Outer service is captured so we can reuse parser/cache logic.
     */
    final class GroovyToolCallback implements ToolCallback {
        private final TurCustomToolDomain tool;
        private final ToolDefinition definition;
        private final RichReturn richReturn;

        GroovyToolCallback(TurCustomToolDomain tool) {
            this.tool = tool;
            this.richReturn = RichReturn.from(tool.returnType());
            String description = tool.llmDescription() == null ? "" : tool.llmDescription();
            if (richReturn != null) {
                description = description + richReturn.descriptionHint;
            }
            this.definition = new DefaultToolDefinition(
                    sanitizeName(tool.title()),
                    description,
                    buildInputSchema(tool));
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return new DefaultToolMetadata(false);
        }

        @Override
        public String call(String toolInput) {
            try {
                String result = executeGroovy(tool, toolInput);
                return richReturn != null ? richReturn.wrap(result) : result;
            } catch (Exception e) {
                log.error("[CustomTool] '{}' failed: {}", tool.title(), e.getMessage(), e);
                return "Error executing tool: " + e.getMessage();
            }
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            // Two-arg path: thread the context through so scripts can read
            // the conversation id and write back into the slot map (e.g. to
            // drive a React card grid). When toolContext is null we fall
            // back to the legacy one-arg execution — keeps unit-test paths
            // that bypass the chat executor working.
            try {
                String result = executeGroovy(tool, toolInput, toolContext);
                return richReturn != null ? richReturn.wrap(result) : result;
            } catch (Exception e) {
                log.error("[CustomTool] '{}' failed: {}", tool.title(), e.getMessage(), e);
                return "Error executing tool: " + e.getMessage();
            }
        }
    }

    /** LLMs reject tool names with spaces; the title is friendlier so we coerce. */
    static String sanitizeName(String title) {
        if (title == null || title.isBlank()) {
            return "custom_tool";
        }
        String sanitized = title.trim().toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isEmpty() ? "custom_tool" : sanitized;
    }
}
