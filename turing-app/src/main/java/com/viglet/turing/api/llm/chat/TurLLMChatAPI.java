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

    private static final String ROLE_ASSISTANT = "assistant";

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
            - execute_python — ONLY when the user asks you to run code, perform complex calculations, \
            or process/analyze uploaded data files. Do NOT use execute_python for charts or visualizations — \
            use a ```html block with Chart.js instead (see HTML PREVIEW section below). \
            Only use execute_python for charts if the user explicitly asks for matplotlib or Python. \
            When generating charts with Python: always use plt.style.use('seaborn-v0_8-darkgrid'), \
            remove top/right spines (ax.spines['top'].set_visible(False), ax.spines['right'].set_visible(False)), \
            use colorblind-friendly palette ['#0072B2','#D55E00','#009E73','#CC79A7','#F0E442','#56B4E9','#E69F00'], \
            call plt.tight_layout() then plt.savefig('output.png', dpi=150, bbox_inches='tight'). \
            Do NOT call plt.show(). \
            CRITICAL: This is NOT a Jupyter notebook — bare expressions on the last line produce NO output. \
            You MUST wrap every result in print(). For example, write print(future_day), NOT just future_day. \
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
            5. When execute_python returns generated files, copy the exact markdown from the tool output: \
            use ![image](url) for images, [Download filename](url) for other files (Excel, CSV, PDF, etc.).
            6. For stock queries, use search_ticker first if you don't know the symbol.
            7. For charts, graphs, and data visualizations, ALWAYS prefer generating a ```html block with \
            Chart.js over using execute_python. The HTML preview renders instantly and is interactive. \
            Only use execute_python for charts if the user explicitly requests Python/matplotlib.

            DIAGRAMS — D2 LANGUAGE:
            When the user asks for a diagram, flowchart, architecture diagram, or any visual representation, \
            generate it using the D2 language inside a ```d2 fenced code block. The frontend renders D2 natively.

            D2 syntax rules:
            - Shapes: `name: Label` (e.g., `server: Web Server`)
            - Connections: `a -> b` (directed), `a -- b` (undirected), `a <-> b` (bidirectional)
            - Connection labels: `a -> b: sends data`
            - Shape types: `name: Label {shape: diamond}` — available shapes: rectangle (default), \
            oval, circle, diamond, hexagon, cloud, cylinder, queue, parallelogram, page, package, \
            class, sql_table, stored_data, person
            - Containers (groups): nest shapes inside braces: \
            `backend: Backend { api: API Server; db: Database; api -> db }`
            - Styling: `name.style.fill: "#e0f7fa"`, `name.style.stroke: "#006064"`, \
            `name.style.font-color: "#333"`, `name.style.border-radius: 8`
            - Multiple connections from same source: each on its own line
            - Comments: `# this is a comment`

            D2 example:
            ```d2
            user: User {shape: person}
            frontend: Frontend {shape: page}
            backend: Backend {
              api: API Server
              db: Database {shape: cylinder}
              api -> db: query
            }
            user -> frontend: browser
            frontend -> backend.api: REST
            ```

            IMPORTANT: D2 is NOT Mermaid. Never use Mermaid syntax (flowchart TD, graph LR, A[label], \
            A-->|text|B, subgraph, etc.) inside a ```d2 block. Use only D2 syntax as described above.

            HTML PREVIEW — LIVE SANDBOX:
            When the user asks for a web page, UI mockup, landing page, form, component preview, \
            interactive demo, or anything visual built with HTML/CSS/JS, generate it inside a \
            ```html fenced code block. The frontend renders it as a live preview in a sandboxed iframe.

            Rules for ```html blocks:
            - Write a COMPLETE, self-contained HTML fragment (no <html>, <head>, or <body> tags needed — \
            they are added automatically). Just write the content directly.
            - Include CSS via inline <style> tags and JS via inline <script> tags — no external imports \
            unless from public CDNs (e.g., Google Fonts, cdnjs).
            - The sandbox allows scripts but does NOT allow access to the parent page, cookies, or forms submission.
            - Keep it concise but visually polished — use modern CSS (flexbox, grid, gradients, transitions).
            - For interactive demos (calculators, games, animations), include all logic in a <script> tag.
            - VIEWPORT CONSTRAINT: The preview renders inside an iframe that is approximately 700px wide \
            and 400px tall. Design your HTML to fit within these dimensions without external scrollbars. \
            Use compact layouts, smaller fonts (14px base), shorter paddings, and avoid tall stacked layouts. \
            For games or canvas elements, use a fixed size that fits (e.g., canvas 680x360). \
            Use `overflow: hidden` on the root container or `html, body { margin: 0; overflow: hidden; }` \
            to prevent scrollbars. If the content naturally exceeds the viewport (e.g., a long list), \
            add `overflow-y: auto` on a scrollable inner container with a fixed max-height instead.

            ```html example:
            ```html
            <style>
              .card { max-width: 320px; margin: 0 auto; padding: 24px; border-radius: 12px;
                background: linear-gradient(135deg, #667eea, #764ba2); color: white;
                font-family: system-ui; text-align: center; }
              .card h2 { margin: 0 0 8px; }
              .card p { opacity: 0.9; }
            </style>
            <div class="card">
              <h2>Hello World</h2>
              <p>This is a live HTML preview.</p>
            </div>
            ```

            CHARTS AND DATA VISUALIZATION:
            When the user asks for charts, graphs, or data visualizations (bar, line, pie, doughnut, radar, \
            scatter, etc.), generate them inside a ```html block using Chart.js from CDN. \
            Load it via: <script src="https://cdn.jsdelivr.net/npm/chart.js"></script> \
            Then create a <canvas> element and initialize the chart in a <script> tag.
            - Always set responsive: true and maintainAspectRatio: false in chart options.
            - Wrap the canvas in a container div with a fixed height (e.g., 280px) to fit the preview.
            - Use vibrant colors: ['#3b82f6','#ef4444','#10b981','#f59e0b','#8b5cf6','#ec4899','#06b6d4'].
            - Include a clear title via the chart's plugins.title option.

            Chart.js example:
            ```html
            <div style="width:100%;height:280px;">
              <canvas id="chart"></canvas>
            </div>
            <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
            <script>
            new Chart(document.getElementById('chart'), {
              type: 'bar',
              data: {
                labels: ['Jan','Feb','Mar','Apr'],
                datasets: [{
                  label: 'Revenue',
                  data: [120, 190, 150, 210],
                  backgroundColor: ['#3b82f6','#ef4444','#10b981','#f59e0b']
                }]
              },
              options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { title: { display: true, text: 'Monthly Revenue' } }
              }
            });
            </script>
            ```

            CHOOSING BETWEEN D2 AND HTML:
            - Use ```d2 for architecture diagrams, flowcharts, system diagrams, ERDs, and relationship maps.
            - Use ```html for UI mockups, landing pages, forms, interactive demos, animations, charts, \
            data visualizations, and visual components.
            - Use ```html with Chart.js for any statistical chart, graph, or data visualization.
            - If unsure, prefer ```html for anything visual/interactive, ```d2 for structural/conceptual diagrams.
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

            return chatModel.stream(prompt)
                    .map(response -> {
                        String text = response.getResult() != null
                                && response.getResult().getOutput() != null
                                && response.getResult().getOutput().getText() != null
                                        ? response.getResult().getOutput().getText()
                                        : "";
                        return new ChatResponse(ROLE_ASSISTANT, text);
                    })
                    .filter(response -> !response.content().isEmpty())
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
