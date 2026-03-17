package com.viglet.turing.api.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import java.util.HashSet;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.llm.chat.TurLLMChatAPI;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurLoggingToolCallback;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are an AI assistant. Answer the user's questions using the tools available to you.
            If you have access to MCP server tools, use them when relevant to fulfill the user's request.
            If the user asks in a specific language, respond in that same language.
            """;

    private final TurAIAgentRepository turAIAgentRepository;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurNativeToolService nativeToolService;
    private final TurMcpToolCallbackService mcpToolCallbackService;
    private final TurLLMTokenUsageService tokenUsageService;

    public TurAIAgentChatAPI(TurAIAgentRepository turAIAgentRepository,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurNativeToolService nativeToolService,
            TurMcpToolCallbackService mcpToolCallbackService,
            TurLLMTokenUsageService tokenUsageService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.nativeToolService = nativeToolService;
        this.mcpToolCallbackService = mcpToolCallbackService;
        this.tokenUsageService = tokenUsageService;
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

        String username = resolveUsername();

        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(turLLMInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());
        ChatModel chatModel = provider.createChatModel(turLLMInstance, decryptedApiKey);

        // Native tools: use only those selected for this agent (comma-separated in nativeTools field)
        Set<String> selectedNativeTools = parseNativeTools(agent.getNativeTools());
        ToolCallback[] nativeCallbacks = selectedNativeTools.isEmpty()
                ? new ToolCallback[0]
                : nativeToolService.getToolCallbacks(selectedNativeTools);

        // Only load MCP servers configured for this agent
        Set<TurMcpServer> agentMcpServers = agent.getMcpServers();
        ToolCallback[] mcpCallbacks = agentMcpServers.isEmpty()
                ? new ToolCallback[0]
                : mcpToolCallbackService.getToolCallbacks(agentMcpServers);

        ToolCallback[] allCallbacks = TurLoggingToolCallback.wrap(
                Stream.concat(
                        Arrays.stream(nativeCallbacks),
                        Arrays.stream(mcpCallbacks)
                ).toArray(ToolCallback[]::new));

        log.info("[AgentChat] Agent '{}': {} tool callbacks ({} native + {} MCP)",
                agent.getTitle(), allCallbacks.length, nativeCallbacks.length, mcpCallbacks.length);

        // Log tool names for debugging
        for (ToolCallback cb : allCallbacks) {
            log.info("[AgentChat] Available tool: '{}' - {}",
                    cb.getToolDefinition().name(),
                    cb.getToolDefinition().description());
        }

        var chatOptions = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(allCallbacks)
                .internalToolExecutionEnabled(true)
                .build();

        String systemPrompt = agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()
                ? agent.getSystemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (ChatMessageItem item : request.messages()) {
            if ("assistant".equals(item.role())) {
                messages.add(new AssistantMessage(item.content()));
            } else {
                messages.add(new UserMessage(item.content()));
            }
        }

        Prompt prompt = new Prompt(messages, chatOptions);

        return Mono.fromCallable(() -> {
            log.info("[AgentChat] Calling LLM for agent '{}', LLM '{}'",
                    agent.getTitle(), turLLMInstance.getTitle());
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(turLLMInstance, response, username);

            String text = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText()
                            : "";
            log.info("[AgentChat] Agent '{}' response: {} chars", agent.getTitle(), text.length());
            return new ChatResponse("assistant", text);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[AgentChat] Agent '{}' error: {}",
                        agent.getTitle(), err.getMessage(), err))
                .filter(response -> !response.content().isEmpty())
                .flux();
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

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    private static Set<String> parseNativeTools(String nativeTools) {
        if (nativeTools == null || nativeTools.isBlank()) {
            return new HashSet<>();
        }
        Set<String> result = new HashSet<>();
        for (String tool : nativeTools.split(",")) {
            String trimmed = tool.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
