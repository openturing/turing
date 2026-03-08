package com.viglet.turing.api.llm.chat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

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

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");

    private static final String SYSTEM_PROMPT = """
            You are a helpful, friendly AI assistant. Your primary job is to have natural conversations \
            with the user. For greetings, general questions, opinions, explanations, or any topic that \
            does NOT require real-time data, just respond directly and naturally — do NOT use any tool.

            You also have access to optional tools. ONLY use them when the user's request clearly \
            requires one. Here is when to use each tool:

            - fetch_webpage / extract_links — ONLY when the user gives you a specific URL (http/https). \
            Never treat normal text as a URL.
            - get_weather — ONLY when the user explicitly asks about weather or climate for a city.
            - get_stock_quote / search_ticker — ONLY when the user explicitly asks about stock prices, \
            market data, or financial quotes.
            - execute_python — ONLY when the user asks you to calculate, process data, generate charts, \
            or run code. When generating charts: always use plt.style.use('seaborn-v0_8-darkgrid'), \
            remove top/right spines (ax.spines['top'].set_visible(False), ax.spines['right'].set_visible(False)), \
            use colorblind-friendly palette ['#0072B2','#D55E00','#009E73','#CC79A7','#F0E442','#56B4E9','#E69F00'], \
            call plt.tight_layout() then plt.savefig('output.png', dpi=150, bbox_inches='tight'). \
            Do NOT call plt.show(). Always use print() for output. \
            If the code produces errors or warnings, analyze the issue, fix the code, and call \
            execute_python again with the corrected version (up to 3 retries).
            - search_images — ONLY when the user asks to see, show, or find a photo, picture, or image \
            of something. Before calling this tool, act as a Prompt Engineer: transform the user's simple \
            request into an optimized English super-query by enriching the subject with texture/color/state \
            adjectives, placing it in a specific scene, and appending photography terms (shallow depth of field, \
            8k, volumetric lighting, sharp focus, cinematic style). Pass ONLY the optimized English query. \
            After getting results, display images using markdown: ![description](image_url).
            - get_current_time — ONLY when the user asks what time it is, the current date, or the \
            date/time in a specific city or timezone. Map city names to IANA timezone identifiers \
            (e.g., 'São Paulo' → 'America/Sao_Paulo', 'Tokyo' → 'Asia/Tokyo'). Use 'UTC' as default.

            IMPORTANT RULES:
            1. DEFAULT BEHAVIOR: Respond conversationally. Most messages do NOT need tools.
            2. If the user asks in a specific language, respond in that same language.
            3. Only use a tool when you are certain the user wants real-time or computed data.
            4. After using a tool, summarize the results clearly.
            5. When execute_python returns a file URL, include it in your response.
            6. For stock queries, use search_ticker first if you don't know the symbol.
            """;

    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurWebCrawlerToolService webCrawlerToolService;
    private final TurWeatherToolService weatherToolService;
    private final TurFinanceToolService financeToolService;
    private final TurCodeInterpreterToolService codeInterpreterToolService;
    private final TurImageSearchToolService imageSearchToolService;
    private final TurDateTimeToolService dateTimeToolService;
    private final TurLLMTokenUsageService tokenUsageService;

    public TurLLMChatAPI(TurLLMInstanceRepository turLLMInstanceRepository,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurWebCrawlerToolService webCrawlerToolService,
            TurWeatherToolService weatherToolService,
            TurFinanceToolService financeToolService,
            TurCodeInterpreterToolService codeInterpreterToolService,
            TurImageSearchToolService imageSearchToolService,
            TurDateTimeToolService dateTimeToolService,
            TurLLMTokenUsageService tokenUsageService) {
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.webCrawlerToolService = webCrawlerToolService;
        this.weatherToolService = weatherToolService;
        this.financeToolService = financeToolService;
        this.codeInterpreterToolService = codeInterpreterToolService;
        this.imageSearchToolService = imageSearchToolService;
        this.dateTimeToolService = dateTimeToolService;
        this.tokenUsageService = tokenUsageService;
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
        springMessages.add(new SystemMessage(SYSTEM_PROMPT));
        springMessages.addAll(buildMessages(request.messages(), files));

        Prompt prompt;
        if (turLLMInstance.isToolsEnabled()) {
            ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
                    .toolObjects(webCrawlerToolService, weatherToolService, financeToolService,
                            codeInterpreterToolService, imageSearchToolService, dateTimeToolService)
                    .build()
                    .getToolCallbacks();

            log.info("[Chat] Registered {} tool callbacks for LLM instance {}", toolCallbacks.length, id);

            var chatOptions = DefaultToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .internalToolExecutionEnabled(true)
                    .build();

            prompt = new Prompt(springMessages, chatOptions);
        } else {
            log.info("[Chat] Tools disabled for LLM instance {}", id);
            prompt = new Prompt(springMessages);
        }

        // Use call() instead of stream() to ensure tool calling works reliably.
        // Spring AI's stream() + internalToolExecutionEnabled(true) does not execute
        // tools correctly with some providers (e.g. Anthropic CONTENT_BLOCK_STOP
        // issue).
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
            return new ChatResponse("assistant", text);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[Chat] Call error: {}", err.getMessage(), err))
                .filter(response -> !response.content().isEmpty())
                .flux();
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }

    private List<Message> buildMessages(List<ChatMessageItem> items, List<MultipartFile> files) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ChatMessageItem item = items.get(i);
            if ("assistant".equals(item.role())) {
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
