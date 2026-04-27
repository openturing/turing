package com.viglet.turing.api.agent;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.llm.chat.TurLLMChatAPI;
import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
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
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurAgentChatExecutor agentChatExecutor;

    public TurAIAgentChatAPI(TurAIAgentRepository turAIAgentRepository,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurAgentChatExecutor agentChatExecutor) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.agentChatExecutor = agentChatExecutor;
    }

    public record AgentChatRequest(String llmInstanceId, List<ChatMessageItem> messages) {
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
        return agentChatExecutor.execute(agent, turLLMInstance, mapped, null)
                .map(r -> new ChatResponse(r.role(), r.content()));
    }

    @GetMapping("/chat/context-info")
    public TurLLMChatAPI.ContextInfoResponse contextInfo(
            @PathVariable String agentId,
            @org.springframework.web.bind.annotation.RequestParam String llmInstanceId) {

        turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("AI Agent not found: " + agentId));

        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(llmInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + llmInstanceId));

        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(turLLMInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());

        var fetched = provider.fetchContextWindow(turLLMInstance, decryptedApiKey);
        if (fetched.isPresent()) {
            return new TurLLMChatAPI.ContextInfoResponse(fetched.getAsInt(), "provider");
        }

        int stored = turLLMInstance.getContextWindow() != null
                ? turLLMInstance.getContextWindow()
                : 128000;
        return new TurLLMChatAPI.ContextInfoResponse(stored, "config");
    }

}
