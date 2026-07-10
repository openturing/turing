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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Cross-agent delegation helper exposed to Custom Tool Groovy scripts as
 * the binding variable {@code agent}. Lets an in-flight tool delegate a
 * sub-task to another Turing AI Agent and await its final answer
 * synchronously:
 *
 * <pre>{@code
 * def answer = agent.invoke(
 *     "specialist-legal-agent",                           // target agent id
 *     "Is this contract clause enforceable? ${slots.contract_excerpt}",
 *     [timeout: 30_000]                                   // ms, optional
 * )
 * slots.set("legal_review", answer)
 * }</pre>
 *
 * <p>Each call spawns an isolated child conversation under the target
 * agent's first enabled LLM (or the LLM the caller picks via the
 * {@code llmInstanceId} option). The child conversation id is a fresh
 * UUID; the parent conversation id is captured as the
 * {@code __parentConversationId} value on the child's first turn so
 * downstream observability (T110 — sub-agent scorecard) can reconstruct
 * the call tree.
 *
 * <p><b>Recursion cap</b>: the helper is initialised with the current
 * call depth (0 at the entry tool, incremented per nested
 * {@code agent.invoke(...)}). The default ceiling is 3 — mirrors
 * {@code TRANSPARENT_WALK_MAX} pattern from the chat-flow engine. A call
 * that would exceed the cap returns the literal text
 * {@code [agent.invoke depth cap reached]} rather than throwing, so the
 * orchestrator LLM sees a stable failure mode.
 *
 * <p><b>Blocking semantics</b>: the helper subscribes to the executor's
 * {@code Flux<ChatResponse>} and blocks up to {@code timeout} ms (default
 * 30s, capped at 300s) on a {@code collectList()}. Tokens whose
 * {@code type} is {@code "token"} are concatenated into the returned
 * string; non-token events (e.g. {@code "options"}) are ignored.
 *
 * <p><b>Failure modes</b> (all returned as bracketed sentinel text, never
 * thrown):
 * <ul>
 *   <li>{@code [agent.invoke: agent not found: <id>]}</li>
 *   <li>{@code [agent.invoke: agent disabled: <id>]}</li>
 *   <li>{@code [agent.invoke: agent has no LLM configured]}</li>
 *   <li>{@code [agent.invoke: llm not found: <id>]}</li>
 *   <li>{@code [agent.invoke: llm not allowed for agent]}</li>
 *   <li>{@code [agent.invoke depth cap reached]}</li>
 *   <li>{@code [agent.invoke timeout after <ms>ms]}</li>
 *   <li>{@code [agent.invoke error: <message>]}</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
public class TurCustomToolAgentHelper {

    /** Default ceiling on recursive {@code agent.invoke(...)} calls. */
    public static final int DEFAULT_DEPTH_CAP = 3;

    /** Default timeout (ms) when the caller doesn't pass {@code opts.timeout}. */
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    /** Hard upper bound on timeout so a runaway script can't hold a thread forever. */
    public static final long MAX_TIMEOUT_MS = 300_000L;

    private final TurAgentChatExecutor executor;
    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmRepository;
    private final String parentConversationId;
    private final int currentDepth;
    private final int depthCap;

    public TurCustomToolAgentHelper(TurAgentChatExecutor executor,
            TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmRepository,
            String parentConversationId,
            int currentDepth,
            int depthCap) {
        this.executor = executor;
        this.agentRepository = agentRepository;
        this.llmRepository = llmRepository;
        this.parentConversationId = parentConversationId;
        this.currentDepth = currentDepth;
        this.depthCap = depthCap < 0 ? DEFAULT_DEPTH_CAP : depthCap;
    }

    /** Convenience overload — uses default depth cap. */
    public TurCustomToolAgentHelper(TurAgentChatExecutor executor,
            TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmRepository,
            String parentConversationId,
            int currentDepth) {
        this(executor, agentRepository, llmRepository, parentConversationId,
                currentDepth, DEFAULT_DEPTH_CAP);
    }

    /**
     * Two-arg form — no options. Uses default timeout, picks the target
     * agent's first enabled LLM.
     */
    public String invoke(String agentId, String message) {
        return invoke(agentId, message, Map.of());
    }

    /**
     * Three-arg form — {@code opts} keys:
     * <ul>
     *   <li>{@code timeout} — Long, ms; default {@link #DEFAULT_TIMEOUT_MS}.</li>
     *   <li>{@code llmInstanceId} — String; defaults to the target agent's first
     *       configured LLM.</li>
     * </ul>
     *
     * <p>Unknown keys are ignored (forward-compatible).
     */
    public String invoke(String agentId, String message, Map<String, ?> opts) {
        if (currentDepth >= depthCap) {
            log.warn("[agent.invoke] depth cap reached: depth={} cap={} target={}",
                    currentDepth, depthCap, agentId);
            return "[agent.invoke depth cap reached]";
        }
        if (agentId == null || agentId.isBlank()) {
            return "[agent.invoke: agent id is required]";
        }
        if (message == null || message.isBlank()) {
            return "[agent.invoke: message is required]";
        }

        TurAIAgent target = agentRepository.findById(agentId).orElse(null);
        if (target == null) {
            return "[agent.invoke: agent not found: " + agentId + "]";
        }
        if (target.getEnabled() != 1) {
            return "[agent.invoke: agent disabled: " + agentId + "]";
        }

        TurLLMInstance llm = resolveLlm(target, opts);
        if (llm == null) {
            return llmNotResolvedMessage(target, opts);
        }

        long timeoutMs = resolveTimeoutMs(opts);

        // Child conversation id: a fresh UUID. Parent linkage is carried in
        // the user-message text so the child agent's persona/flow context
        // never sees it as a slot — purely an audit handle. Future T110
        // will read this via a structured field on the chat session event.
        String childConversationId = UUID.randomUUID().toString();
        String wireMessage = parentConversationId == null
                ? message
                : "[__parentConversationId=" + parentConversationId + "]\n" + message;

        return runChildAgent(target, llm, wireMessage, childConversationId, timeoutMs, agentId);
    }

    /** Diagnostic message for why {@code resolveLlm} returned null (no LLM, or a disallowed one). */
    private String llmNotResolvedMessage(TurAIAgent target, Map<String, ?> opts) {
        if (target.getLlmInstances() == null || target.getLlmInstances().isEmpty()) {
            return "[agent.invoke: agent has no LLM configured]";
        }
        // The opts.llmInstanceId either didn't resolve or wasn't allowed.
        Object requested = opts == null ? null : opts.get("llmInstanceId");
        if (requested != null) {
            return "[agent.invoke: llm not allowed for agent: " + requested + "]";
        }
        return "[agent.invoke: llm not found]";
    }

    /** Runs the child agent's chat to completion (bounded by {@code timeoutMs}) and returns its text. */
    private String runChildAgent(TurAIAgent target, TurLLMInstance llm, String wireMessage,
            String childConversationId, long timeoutMs, String agentId) {
        var history = List.of(new TurAgentChatExecutor.ChatMessageItem("user", wireMessage));

        log.info("[agent.invoke] depth={} target={} llm={} childConv={} parentConv={}",
                currentDepth, agentId, llm.getId(), childConversationId, parentConversationId);

        try {
            List<TurAgentChatExecutor.ChatResponse> all = executor.execute(
                            new TurAgentChatRequest(target, llm, history, null,
                                    childConversationId, /* flowId= */ null, /* files= */ null,
                                    /* requestPersonaId= */ null),
                            /* agentInvokeDepth= */ currentDepth)
                    .collectList()
                    .block(Duration.ofMillis(timeoutMs));

            if (all == null) {
                return "[agent.invoke timeout after " + timeoutMs + "ms]";
            }

            StringBuilder out = new StringBuilder();
            for (var r : all) {
                if ("token".equals(r.type()) && r.content() != null) {
                    out.append(r.content());
                }
            }
            return out.toString();
        } catch (IllegalStateException e) {
            // Reactor wraps a TimeoutException from .block(Duration) as
            // IllegalStateException("Timeout on blocking read"). Surface it
            // distinctly from arbitrary other failures.
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("timeout")) {
                return "[agent.invoke timeout after " + timeoutMs + "ms]";
            }
            log.error("[agent.invoke] error invoking agent={} : {}", agentId, e.getMessage(), e);
            return "[agent.invoke error: " + e.getMessage() + "]";
        } catch (RuntimeException e) {
            log.error("[agent.invoke] error invoking agent={} : {}", agentId, e.getMessage(), e);
            return "[agent.invoke error: " + e.getMessage() + "]";
        }
    }

    /** Public for tests + observability — the depth this helper instance was constructed at. */
    public int getCurrentDepth() {
        return currentDepth;
    }

    /** Public for tests + observability — the cap this helper instance enforces. */
    public int getDepthCap() {
        return depthCap;
    }

    private TurLLMInstance resolveLlm(TurAIAgent target, Map<String, ?> opts) {
        Object requested = opts == null ? null : opts.get("llmInstanceId");
        if (requested instanceof String requestedId && !requestedId.isBlank()) {
            boolean allowed = target.getLlmInstances() != null
                    && target.getLlmInstances().stream()
                            .anyMatch(l -> requestedId.equals(l.getId()));
            if (!allowed) {
                return null;
            }
            return llmRepository.findById(requestedId).orElse(null);
        }
        if (target.getLlmInstances() == null || target.getLlmInstances().isEmpty()) {
            return null;
        }
        // Pick the first allowed LLM that still exists + is enabled.
        for (TurLLMInstance candidate : target.getLlmInstances()) {
            TurLLMInstance fresh = llmRepository.findById(candidate.getId()).orElse(null);
            if (fresh != null && fresh.getEnabled() == 1) {
                return fresh;
            }
        }
        return null;
    }

    private static long resolveTimeoutMs(Map<String, ?> opts) {
        if (opts == null) return DEFAULT_TIMEOUT_MS;
        Object raw = opts.get("timeout");
        if (raw == null) return DEFAULT_TIMEOUT_MS;
        long requested = switch (raw) {
            case Number n -> n.longValue();
            case String s -> {
                try {
                    yield Long.parseLong(s.trim());
                } catch (NumberFormatException e) {
                    yield 0L; // unparseable → falls through to the default below
                }
            }
            default -> 0L;
        };
        if (requested <= 0) return DEFAULT_TIMEOUT_MS;
        return Math.min(requested, MAX_TIMEOUT_MS);
    }
}
