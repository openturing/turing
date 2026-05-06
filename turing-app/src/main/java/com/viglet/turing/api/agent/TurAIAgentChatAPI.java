package com.viglet.turing.api.agent;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.llm.chat.TurLLMChatAPI;
import com.viglet.turing.domain.agent.TurAIAgentRepositoryPort;
import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Chat endpoint for AI Agent. Each agent has its own LLM instances,
 * MCP servers and system prompt.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/ai-agent/{agentId}")
@Tag(name = "AI Agent Chat", description = "Chat using AI Agent configuration")
public class TurAIAgentChatAPI {

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurAIAgentRepositoryPort turAIAgentRepositoryPort;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurLLMInstanceRepositoryPort turLLMInstanceRepositoryPort;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurAgentChatExecutor agentChatExecutor;
    private final TurChatFlowRepository turChatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;

    public TurAIAgentChatAPI(TurAIAgentRepository turAIAgentRepository,
            TurAIAgentRepositoryPort turAIAgentRepositoryPort,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMInstanceRepositoryPort turLLMInstanceRepositoryPort,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurAgentChatExecutor agentChatExecutor,
            TurChatFlowRepository turChatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentRepositoryPort = turAIAgentRepositoryPort;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMInstanceRepositoryPort = turLLMInstanceRepositoryPort;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.agentChatExecutor = agentChatExecutor;
        this.turChatFlowRepository = turChatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
    }

    public record AgentChatRequest(
            String llmInstanceId,
            List<ChatMessageItem> messages,
            String conversationId,
            String flowId) {
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content) {
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatResponse> chat(
            @PathVariable String agentId,
            @org.springframework.web.bind.annotation.RequestBody AgentChatRequest request) {

        TurAIAgent agent = turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("AI Agent not found: " + agentId));

        if (agent.getEnabled() != 1) {
            throw new IllegalArgumentException("AI Agent is disabled: " + agentId);
        }

        String llmInstanceId = request.llmInstanceId();
        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(llmInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + llmInstanceId));

        boolean llmAllowed = agent.getLlmInstances().stream()
                .anyMatch(llm -> llm.getId().equals(llmInstanceId));
        if (!llmAllowed) {
            throw new IllegalArgumentException(
                    "LLM instance '" + llmInstanceId + "' is not configured for agent '" + agentId + "'");
        }

        log.info("[AgentChat] Agent '{}' received request with {} messages, LLM: {}",
                agent.getTitle(), request.messages().size(), turLLMInstance.getTitle());

        // All the tool resolution + chat model + streaming logic is centralized
        // in TurAgentChatExecutor so the SN site AI Mode can reuse the same
        // pipeline with an augmented system prompt (for RAG context).
        List<TurAgentChatExecutor.ChatMessageItem> mapped = request.messages().stream()
                .map(m -> new TurAgentChatExecutor.ChatMessageItem(m.role(), m.content()))
                .toList();
        return agentChatExecutor.execute(agent, turLLMInstance, mapped, null,
                        request.conversationId(), request.flowId())
                .map(r -> new ChatResponse(r.role(), r.content()));
    }

    /**
     * Resets the runtime state of a chat flow for a given conversation. The
     * next chat turn rebuilds the state at the START node, freeing the user
     * from any guard rail enforcement that was anchored on the previous
     * current node.
     */
    @Transactional
    @DeleteMapping("/chat-flow-state")
    public boolean resetFlowState(@PathVariable String agentId,
            @RequestParam String flowId,
            @RequestParam String conversationId) {

        TurChatFlow flow = turChatFlowRepository.findById(flowId)
                .orElseThrow(() -> new IllegalArgumentException("Chat flow not found: " + flowId));

        // Defense in depth: a flow may only be reset by the agent that owns it.
        if (flow.getTurAIAgent() == null || !agentId.equals(flow.getTurAIAgent().getId())) {
            throw new IllegalArgumentException(
                    "Chat flow '" + flowId + "' does not belong to agent '" + agentId + "'");
        }

        return chatFlowEngineService.resetState(conversationId, flowId);
    }

    @GetMapping("/chat/context-info")
    public TurLLMChatAPI.ContextInfoResponse contextInfo(
            @PathVariable String agentId,
            @org.springframework.web.bind.annotation.RequestParam String llmInstanceId) {

        // Agent existence check via the port — no agent entity is needed
        // downstream in the contextInfo flow (only the LLM is consulted).
        turAIAgentRepositoryPort.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("AI Agent not found: " + agentId));

        // LLM existence + non-secret read via the port. The configured
        // contextWindow is part of the public domain projection and is
        // used for the fall-back "config" branch below.
        TurLLMInstanceDomain llmDomain = turLLMInstanceRepositoryPort.findById(llmInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + llmInstanceId));

        // Provider call still needs the JPA entity — the encrypted API key
        // and provider-specific options live on the entity per the secrets
        // policy, and the provider factory keys off the entity.
        // Both the port adapter and findById hit the same Spring cache,
        // so this is not an extra round-trip.
        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(llmInstanceId)
                .orElseThrow(() -> new IllegalStateException(
                        "LLM instance vanished between port and entity load: " + llmInstanceId));
        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(turLLMInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());

        var fetched = provider.fetchContextWindow(turLLMInstance, decryptedApiKey);
        if (fetched.isPresent()) {
            return new TurLLMChatAPI.ContextInfoResponse(fetched.getAsInt(), "provider");
        }

        int stored = llmDomain.contextWindow() != null ? llmDomain.contextWindow() : 128000;
        return new TurLLMChatAPI.ContextInfoResponse(stored, "config");
    }

}
