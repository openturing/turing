/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurChatMessage;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.genai.TurGenAiContextFactory;
import com.viglet.turing.genai.TurSNGenAi;
import com.viglet.turing.genai.gateway.TurOpenAiWire.WireMessage;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;

/**
 * T744 / §XLIX — the differentiator: routes a gateway {@code model} name into the
 * Turing stack itself, so an OpenAI-compatible client gets Turing's agents, RAG
 * and $0 local model just by changing the {@code model} string (the moat pure
 * proxies cannot copy):
 * <ul>
 *   <li>{@code turing-agent:<id>} → the full agent via {@link TurAgentChatExecutor}
 *       (system prompt + tools + MCP + grounding);</li>
 *   <li>{@code turing-sn:<site>} → a RAG-grounded answer over a Semantic
 *       Navigation site via {@link TurSNGenAi};</li>
 *   <li>{@code turing-local:*} → forces the embedded ($0) provider instance
 *       (resolved here; execution pending the embedded runtime).</li>
 * </ul>
 * Plus {@link #retrieveContext} for {@code x-turing-rag-site} = RAG-as-a-header on
 * any base model.
 *
 * <p>Agent/SN answers are produced blocking and returned as plain text; the
 * gateway service wraps them into the OpenAI wire format (and replays them as a
 * single streamed chunk when {@code stream:true}). Side events (sources, option
 * chips) are dropped for wire compatibility.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurGatewayModelRouter {

    public static final String AGENT_PREFIX = "turing-agent:";
    public static final String SN_PREFIX = "turing-sn:";
    public static final String LOCAL_PREFIX = "turing-local:";
    /** T795 — vectorless (structured-data) RAG copilot over an SN site. */
    public static final String COPILOT_PREFIX = "turing-copilot:";
    private static final String EMBEDDED_PLUGIN = "embedded-jlama";
    private static final String TOKEN = "token";
    private static final String ROLE_ASSISTANT = "assistant";

    private final TurAIAgentRepository agentRepository;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurAgentChatExecutor agentChatExecutor;
    private final TurSNSearchProcess snSearchProcess;
    private final TurGenAiContextFactory contextFactory;
    private final TurSNGenAi snGenAi;
    private final com.viglet.turing.genai.catalog.TurCatalogCopilotService catalogCopilotService;

    public TurGatewayModelRouter(TurAIAgentRepository agentRepository,
            TurLLMInstanceRepository llmInstanceRepository,
            TurAgentChatExecutor agentChatExecutor,
            TurSNSearchProcess snSearchProcess,
            TurGenAiContextFactory contextFactory,
            TurSNGenAi snGenAi,
            com.viglet.turing.genai.catalog.TurCatalogCopilotService catalogCopilotService) {
        this.agentRepository = agentRepository;
        this.llmInstanceRepository = llmInstanceRepository;
        this.agentChatExecutor = agentChatExecutor;
        this.snSearchProcess = snSearchProcess;
        this.contextFactory = contextFactory;
        this.snGenAi = snGenAi;
        this.catalogCopilotService = catalogCopilotService;
    }

    // ---- Prefix detection -------------------------------------------------

    public boolean isAgent(String model) {
        return model != null && model.startsWith(AGENT_PREFIX);
    }

    public boolean isSn(String model) {
        return model != null && model.startsWith(SN_PREFIX);
    }

    public boolean isLocal(String model) {
        return model != null && model.startsWith(LOCAL_PREFIX);
    }

    public boolean isCopilot(String model) {
        return model != null && model.startsWith(COPILOT_PREFIX);
    }

    /** True for any {@code turing-*} model that routes into the stack (not plain passthrough). */
    public boolean isNative(String model) {
        return isAgent(model) || isSn(model) || isCopilot(model);
    }

    // ---- turing-agent -----------------------------------------------------

    /** Blocking agent answer (assembled from the agent pipeline's token output). */
    public String agentAnswer(String model, List<WireMessage> messages) {
        String agentId = model.substring(AGENT_PREFIX.length()).trim();
        TurAIAgent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("AI Agent not found: " + agentId));
        if (agent.getEnabled() != 1) {
            throw new IllegalArgumentException("AI Agent is disabled: " + agentId);
        }
        TurLLMInstance llmInstance = agent.getLlmInstances().stream()
                .min(Comparator.comparing(TurLLMInstance::getId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "AI Agent '" + agentId + "' has no LLM instance configured"));
        List<TurAgentChatExecutor.ChatMessageItem> history = messages == null ? List.of()
                : messages.stream()
                        .map(m -> new TurAgentChatExecutor.ChatMessageItem(m.role(), m.content()))
                        .toList();
        List<String> parts = agentChatExecutor.execute(
                        new TurAgentChatRequest(agent, llmInstance, history, null, null, null, null, null))
                .filter(r -> TOKEN.equals(r.type()) && r.content() != null && !r.content().isEmpty())
                .map(TurAgentChatExecutor.ChatResponse::content)
                .collectList()
                .block();
        return parts == null ? "" : String.join("", parts);
    }

    // ---- turing-sn --------------------------------------------------------

    /** Blocking RAG-grounded answer over a Semantic Navigation site. */
    public String snAnswer(String model, List<WireMessage> messages) {
        String siteName = model.substring(SN_PREFIX.length()).trim();
        TurSNSite site = snSearchProcess.getSNSite(siteName)
                .orElseThrow(() -> new IllegalArgumentException("SN site not found: " + siteName));
        TurGenAiContext context = contextFactory.build(site.getTurSNSiteGenAi());
        if (!context.isEnabled()) {
            throw new IllegalStateException("RAG is not available for SN site: " + siteName);
        }
        List<TurSNGenAi.ConversationMessage> history = messages == null ? List.of()
                : messages.stream()
                        .map(m -> new TurSNGenAi.ConversationMessage(m.role(), m.content()))
                        .toList();
        TurChatMessage answer = snGenAi.assistantConversation(context, history);
        return (answer == null || answer.getText() == null) ? "" : answer.getText();
    }

    // ---- turing-copilot (T795) -------------------------------------------

    /**
     * T795 / §LIV.6 — a blocking Vectorless (Structured-Data) RAG answer over an SN
     * site via {@link com.viglet.turing.genai.catalog.TurCatalogCopilotService}: the
     * question is grounded on the site's declared field schema (no embeddings), and
     * the default LLM answers strictly from the cited rows. The cited sources are
     * appended as a plain-text "Sources" footer so an OpenAI-style client keeps the
     * citations even though the wire format carries no side channel. Works from the
     * {@code turing-copilot:<site>} model route or the {@code x-turing-copilot-site}
     * header (the caller passes the resolved site name).
     */
    public String copilotAnswer(String siteName, List<WireMessage> messages) {
        if (!StringUtils.hasText(siteName)) {
            throw new IllegalArgumentException("turing-copilot requires a site name");
        }
        List<com.viglet.turing.genai.catalog.TurCatalogCopilotService.ChatTurn> turns =
                messages == null ? List.of()
                        : messages.stream()
                                .map(m -> new com.viglet.turing.genai.catalog.TurCatalogCopilotService.ChatTurn(
                                        m.role(), m.content()))
                                .toList();
        var result = catalogCopilotService.answer(siteName, null, turns);
        if (!result.available()) {
            throw new IllegalStateException(result.error() != null ? result.error()
                    : "Vectorless copilot is not available for SN site: " + siteName);
        }
        StringBuilder out = new StringBuilder();
        if (StringUtils.hasText(result.answer())) {
            out.append(result.answer());
        }
        appendSources(out, result.citations());
        return out.toString();
    }

    /** Renders the cited catalog rows as a plain-text footer (id/title + link). */
    private static void appendSources(StringBuilder out,
            List<com.viglet.turing.genai.catalog.TurCatalogCitation> citations) {
        if (citations == null || citations.isEmpty()) {
            return;
        }
        if (out.length() > 0) {
            out.append("\n\n");
        }
        out.append("Sources:");
        for (var c : citations) {
            out.append("\n[").append(c.rank()).append("] ")
                    .append(c.title() != null ? c.title() : c.id());
            if (StringUtils.hasText(c.url())) {
                out.append(" — ").append(c.url());
            }
        }
    }

    // ---- turing-local -----------------------------------------------------

    /**
     * Resolves the enabled embedded ($0) LLM instance for {@code turing-local:*}.
     * The routing seam is complete; the embedded runtime itself is a scaffold, so
     * a subsequent call may still fail until it is wired.
     */
    public TurLLMInstance resolveEmbeddedInstance() {
        return llmInstanceRepository.findAll().stream()
                .filter(i -> i.getEnabled() == 1 && isEmbeddedVendor(i))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No enabled embedded (turing-local) LLM instance is configured"));
    }

    private boolean isEmbeddedVendor(TurLLMInstance instance) {
        var vendor = instance.getTurLLMVendor();
        if (vendor == null) {
            return false;
        }
        String plugin = StringUtils.hasText(vendor.getPlugin()) ? vendor.getPlugin() : vendor.getId();
        return EMBEDDED_PLUGIN.equalsIgnoreCase(plugin);
    }

    // ---- x-turing-rag-site ------------------------------------------------

    /**
     * Retrieves the top passages for {@code siteName} matching {@code query}, for
     * injection as a system-prompt prefix on a base model (RAG-as-a-header).
     * Returns {@code null} when the site is unknown, RAG is off, or nothing hits.
     */
    public String retrieveContext(String siteName, String query) {
        if (siteName == null || siteName.isBlank() || query == null || query.isBlank()) {
            return null;
        }
        try {
            var siteOpt = snSearchProcess.getSNSite(siteName);
            if (siteOpt.isEmpty()) {
                return null;
            }
            TurGenAiContext context = contextFactory.build(siteOpt.get().getTurSNSiteGenAi());
            if (!context.isEnabled() || context.getVectorStore() == null) {
                return null;
            }
            List<Document> docs = context.getVectorStore().similaritySearch(
                    SearchRequest.builder().query(query).topK(6).build());
            if (docs == null || docs.isEmpty()) {
                return null;
            }
            String joined = docs.stream()
                    .map(Document::getText)
                    .filter(t -> t != null && !t.isBlank())
                    .collect(Collectors.joining("\n\n"));
            return joined.isBlank() ? null : joined;
        } catch (RuntimeException e) {
            log.warn("[Gateway] RAG-as-a-header retrieval failed for site {}: {}", siteName, e.getMessage());
            return null;
        }
    }
}
