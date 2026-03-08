package com.viglet.turing.api.llm.chat;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurSemanticNavToolService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
@RequestMapping("/api/v2/llm/{id}/semantic-chat")
@Tag(name = "LLM Semantic Chat", description = "Chat with Semantic Navigation tool calling")
public class TurLLMSemanticChatAPI {

    private static final String SYSTEM_PROMPT = """
            You are a Turing Semantic Navigation assistant. You help users explore and search \
            content indexed in Turing Semantic Navigation sites.

            You have access to tools that allow you to:
            1. List all available Semantic Navigation sites (list_sites)
            2. Get field mappings for a site (get_site_fields)
            3. Search content within a site (search_site)

            CRITICAL RULES:
            1. ALWAYS call list_sites first to discover available sites and their exact locale codes.
            2. The _setlocale parameter in search_site is REQUIRED and you MUST use the EXACT locale \
            string returned by list_sites. For example, if list_sites returns "en" and "pt", you must \
            use exactly "en" or "pt" — NEVER guess or expand to "en_US", "pt_BR", etc. \
            Copy the locale value exactly as shown in the list_sites output.
            3. ALWAYS call get_site_fields before searching to discover facet fields (fields with facet=yes). \
            When the user asks about a specific subject or topic, look for facet fields that match \
            (e.g., category, type, department) and use them as filterQueries to narrow results.
            4. Present search results in a clear, organized format with relevant field values.
            5. If the user asks in a specific language, respond in that same language.
            """;

    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurSemanticNavToolService semanticNavToolService;
    private final TurLLMTokenUsageService tokenUsageService;

    public TurLLMSemanticChatAPI(TurLLMInstanceRepository turLLMInstanceRepository,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurSemanticNavToolService semanticNavToolService,
            TurLLMTokenUsageService tokenUsageService) {
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.semanticNavToolService = semanticNavToolService;
        this.tokenUsageService = tokenUsageService;
    }

    public record ChatRequest(List<ChatMessageItem> messages) {
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content) {
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatResponse> chat(
            @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody ChatRequest request) {

        log.info("[SemanticChat] Received request with {} messages for LLM instance {}",
                request.messages().size(), id);

        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + id));

        String username = resolveUsername();

        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(turLLMInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());
        ChatModel chatModel = provider.createChatModel(turLLMInstance, decryptedApiKey);

        ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(semanticNavToolService)
                .build()
                .getToolCallbacks();

        log.info("[SemanticChat] Registered {} tool callbacks for instance {}", toolCallbacks.length, id);

        var chatOptions = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(true)
                .build();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (ChatMessageItem item : request.messages()) {
            if ("assistant".equals(item.role())) {
                messages.add(new AssistantMessage(item.content()));
            } else {
                messages.add(new UserMessage(item.content()));
            }
        }

        Prompt prompt = new Prompt(messages, chatOptions);

        return Mono.fromCallable(() -> {
            log.info("[SemanticChat] Calling LLM for instance {}", id);
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(turLLMInstance, response, username);

            String text = response.getResult() != null
                    && response.getResult().getOutput() != null
                    && response.getResult().getOutput().getText() != null
                            ? response.getResult().getOutput().getText()
                            : "";
            log.info("[SemanticChat] LLM response: {} chars for instance {}", text.length(), id);
            return new ChatResponse("assistant", text);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[SemanticChat] Call error: {}", err.getMessage(), err))
                .filter(response -> !response.content().isEmpty())
                .flux();
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    @GetMapping("/context-info")
    public TurLLMChatAPI.ContextInfoResponse contextInfo(@PathVariable String id) {
        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + id));

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
