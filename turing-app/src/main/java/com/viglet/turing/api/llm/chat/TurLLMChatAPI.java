package com.viglet.turing.api.llm.chat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurCodeInterpreterToolService;
import com.viglet.turing.genai.tool.TurDateTimeToolService;
import com.viglet.turing.genai.tool.TurFinanceToolService;
import com.viglet.turing.genai.tool.TurImageSearchToolService;
import com.viglet.turing.genai.tool.TurWeatherToolService;
import com.viglet.turing.genai.tool.TurRagSearchToolService;
import com.viglet.turing.genai.tool.TurToolDescriptionCallback;
import com.viglet.turing.genai.tool.TurToolDescriptionService;
import com.viglet.turing.genai.tool.TurIntegrationMonitoringToolService;
import com.viglet.turing.genai.tool.TurLoggingToolService;
import com.viglet.turing.genai.tool.TurSystemInfoToolService;
import com.viglet.turing.genai.tool.TurWebCrawlerToolService;
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
@RequestMapping("/api/v2/llm/{id}/chat")
@Tag(name = "LLM Chat", description = "Chat directly with a Language Model instance")
public class TurLLMChatAPI {

    private static final String ROLE_ASSISTANT = "assistant";

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/base.md")
    private org.springframework.core.io.Resource basePromptResource;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/tools.md")
    private org.springframework.core.io.Resource toolsPromptResource;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/render.md")
    private org.springframework.core.io.Resource renderPromptResource;

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/ops.md")
    private org.springframework.core.io.Resource opsPromptResource;

    private String basePrompt;
    private String toolsPrompt;
    private String renderPrompt;
    private String opsPrompt;

    @PostConstruct
    void loadPrompts() throws IOException {
        basePrompt = basePromptResource.getContentAsString(StandardCharsets.UTF_8);
        toolsPrompt = toolsPromptResource.getContentAsString(StandardCharsets.UTF_8);
        renderPrompt = renderPromptResource.getContentAsString(StandardCharsets.UTF_8);
        opsPrompt = opsPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurWebCrawlerToolService webCrawlerToolService;
    private final TurWeatherToolService weatherToolService;
    private final TurFinanceToolService financeToolService;
    private final TurCodeInterpreterToolService codeInterpreterToolService;
    private final TurImageSearchToolService imageSearchToolService;
    private final TurDateTimeToolService dateTimeToolService;
    private final TurRagSearchToolService ragSearchToolService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final TurToolDescriptionService toolDescriptionService;
    private final TurLoggingToolService loggingToolService;
    private final TurIntegrationMonitoringToolService integrationMonitoringToolService;
    private final TurSystemInfoToolService systemInfoToolService;

    public TurLLMChatAPI(TurLLMInstanceRepository turLLMInstanceRepository,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurWebCrawlerToolService webCrawlerToolService,
            TurWeatherToolService weatherToolService,
            TurFinanceToolService financeToolService,
            TurCodeInterpreterToolService codeInterpreterToolService,
            TurImageSearchToolService imageSearchToolService,
            TurDateTimeToolService dateTimeToolService,
            TurRagSearchToolService ragSearchToolService,
            TurLLMTokenUsageService tokenUsageService,
            TurToolDescriptionService toolDescriptionService,
            TurLoggingToolService loggingToolService,
            TurIntegrationMonitoringToolService integrationMonitoringToolService,
            TurSystemInfoToolService systemInfoToolService) {
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.webCrawlerToolService = webCrawlerToolService;
        this.weatherToolService = weatherToolService;
        this.financeToolService = financeToolService;
        this.codeInterpreterToolService = codeInterpreterToolService;
        this.imageSearchToolService = imageSearchToolService;
        this.dateTimeToolService = dateTimeToolService;
        this.ragSearchToolService = ragSearchToolService;
        this.tokenUsageService = tokenUsageService;
        this.toolDescriptionService = toolDescriptionService;
        this.loggingToolService = loggingToolService;
        this.integrationMonitoringToolService = integrationMonitoringToolService;
        this.systemInfoToolService = systemInfoToolService;
    }

    public record ChatRequest(List<ChatMessageItem> messages) {
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content) {
    }

    public record ContextInfoResponse(int contextWindow, String source) {
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Flux<ChatResponse> chat(
            @PathVariable String id,
            @RequestPart("request") ChatRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return doChat(id, request, files);
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatResponse> chatJson(
            @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody ChatRequest request) {
        return doChat(id, request, null);
    }

    @GetMapping("/context-info")
    public ContextInfoResponse contextInfo(@PathVariable String id) {
        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + id));

        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(turLLMInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());

        OptionalInt fetched = provider.fetchContextWindow(turLLMInstance, decryptedApiKey);
        if (fetched.isPresent()) {
            return new ContextInfoResponse(fetched.getAsInt(), "provider");
        }

        int stored = turLLMInstance.getContextWindow() != null
                ? turLLMInstance.getContextWindow()
                : 128000;
        return new ContextInfoResponse(stored, "config");
    }

    private Flux<ChatResponse> doChat(String id, ChatRequest request, List<MultipartFile> files) {
        TurLLMInstance turLLMInstance = turLLMInstanceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("LLM instance not found: " + id));

        // Capture username from security context on the request thread
        String username = resolveUsername();

        TurGenAiLlmProvider provider = llmProviderFactory.getProvider(turLLMInstance);
        String decryptedApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());
        ChatModel chatModel = provider.createChatModel(turLLMInstance, decryptedApiKey);

        List<Message> springMessages = new ArrayList<>();
        String systemPrompt = turLLMInstance.isToolsEnabled()
                ? basePrompt + toolsPrompt + opsPrompt + renderPrompt
                : basePrompt + renderPrompt;
        springMessages.add(new SystemMessage(systemPrompt));
        springMessages.addAll(buildMessages(request.messages(), files));

        if (turLLMInstance.isToolsEnabled()) {
            // Build base tool objects list, conditionally add RAG search
            java.util.List<Object> toolObjects = new java.util.ArrayList<>(java.util.List.of(
                    webCrawlerToolService, weatherToolService, financeToolService,
                    codeInterpreterToolService, imageSearchToolService, dateTimeToolService,
                    loggingToolService, integrationMonitoringToolService, systemInfoToolService));
            if (ragSearchToolService.isAvailable()) {
                toolObjects.add(ragSearchToolService);
            }

            ToolCallback[] toolCallbacks = TurToolDescriptionCallback.wrap(
                    MethodToolCallbackProvider.builder()
                            .toolObjects(toolObjects.toArray())
                            .build()
                            .getToolCallbacks(),
                    toolDescriptionService);

            log.info("[Chat] Registered {} tool callbacks for LLM instance {} (RAG={})",
                    toolCallbacks.length, id, ragSearchToolService.isAvailable());

            var chatOptions = DefaultToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(true)
                    .build();

            Prompt prompt = new Prompt(springMessages, chatOptions);

            // Use call() instead of stream() to ensure tool calling works reliably.
            // Spring AI's stream() + internalToolExecutionEnabled(true) does not execute
            // tools correctly with some providers (e.g. Anthropic CONTENT_BLOCK_STOP issue).
            return Mono.fromCallable(() -> {
                log.info("[Chat] Calling LLM with tool support for instance {}", id);
                var response = chatModel.call(prompt);

                tokenUsageService.recordUsage(turLLMInstance, response, username);

                String text = response.getResult() != null
                        && response.getResult().getOutput() != null
                        && response.getResult().getOutput().getText() != null
                                ? response.getResult().getOutput().getText()
                                : "";
                log.info("[Chat] LLM response: {} chars for instance {}", text.length(), id);
                return new ChatResponse(ROLE_ASSISTANT, text);
            })
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnError(err -> log.error("[Chat] Call error: {}", err.getMessage(), err))
                    .filter(response -> !response.content().isEmpty())
                    .flux();
        } else {
            log.info("[Chat] Tools disabled, using stream for LLM instance {}", id);
            Prompt prompt = new Prompt(springMessages);

            var lastStreamResponse = new java.util.concurrent.atomic.AtomicReference<
                    org.springframework.ai.chat.model.ChatResponse>();

            return chatModel.stream(prompt)
                    .doOnNext(lastStreamResponse::set)
                    .map(response -> {
                        String text = response.getResult() != null
                                && response.getResult().getOutput() != null
                                && response.getResult().getOutput().getText() != null
                                        ? response.getResult().getOutput().getText()
                                        : "";
                        return new ChatResponse(ROLE_ASSISTANT, text);
                    })
                    .filter(response -> !response.content().isEmpty())
                    .doOnComplete(() -> {
                        var finalResponse = lastStreamResponse.get();
                        if (finalResponse != null) {
                            tokenUsageService.recordUsage(turLLMInstance, finalResponse, username);
                        }
                    })
                    .doOnError(err -> log.error("[Chat] Stream error: {}", err.getMessage(), err));
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    private List<Message> buildMessages(List<ChatMessageItem> items, List<MultipartFile> files) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ChatMessageItem item = items.get(i);
            if (ROLE_ASSISTANT.equals(item.role())) {
                messages.add(new AssistantMessage(item.content()));
            } else {
                boolean isLast = (i == items.size() - 1);
                if (isLast && files != null && !files.isEmpty()) {
                    messages.add(buildUserMessageWithFiles(item.content(), files));
                } else {
                    messages.add(new UserMessage(item.content()));
                }
            }
        }
        return messages;
    }

    private UserMessage buildUserMessageWithFiles(String text, List<MultipartFile> files) {
        List<Media> imageMedia = new ArrayList<>();
        StringBuilder extractedText = new StringBuilder();

        for (MultipartFile file : files) {
            String contentType = file.getContentType() != null
                    ? file.getContentType()
                    : "application/octet-stream";

            // Always extract text from all files (works for PDF, DOCX, and images via OCR)
            String content = extractTextFromFile(file);
            if (!content.isBlank()) {
                extractedText.append("\n\n--- File: ")
                        .append(file.getOriginalFilename())
                        .append(" ---\n")
                        .append(content);
            }

            // Also attach images as Media for vision-capable models
            if (IMAGE_MIME_TYPES.contains(contentType)) {
                try {
                    imageMedia.add(Media.builder()
                            .mimeType(MimeType.valueOf(contentType))
                            .data(new ByteArrayResource(file.getBytes()))
                            .name(file.getOriginalFilename())
                            .build());
                } catch (IOException e) {
                    log.warn("Failed to read image file: {}", file.getOriginalFilename(), e);
                }
            }
        }

        String fullText = extractedText.isEmpty()
                ? text
                : text + extractedText;

        if (!imageMedia.isEmpty()) {
            return UserMessage.builder()
                    .text(fullText)
                    .media(imageMedia)
                    .build();
        }
        return new UserMessage(fullText);
    }

    private String extractTextFromFile(MultipartFile file) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes())) {
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(HttpHeaders.CONTENT_TYPE, file.getContentType());
            new AutoDetectParser().parse(inputStream, handler, metadata, new ParseContext());
            return handler.toString().strip();
        } catch (IOException | TikaException | SAXException e) {
            log.warn("Failed to extract text from file: {}", file.getOriginalFilename(), e);
            return "";
        }
    }
}
