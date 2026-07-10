package com.viglet.turing.api.agent;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.llm.chat.TurLLMChatAPI;
import com.viglet.turing.domain.agent.TurAIAgentRepositoryPort;
import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.genai.TurAgentChatExecutor;
import com.viglet.turing.genai.TurAgentChatRequest;
import com.viglet.turing.genai.TurChatProviderErrorMapper;
import com.viglet.turing.genai.flow.TurChatFlowEngineService;
import com.viglet.turing.genai.nativeapi.TurNativeChatExecutor;
import com.viglet.turing.genai.tool.TurCodeInterpreterUrlSigner;
import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.genai.workspace.TurWorkspaceEvent;
import com.viglet.turing.genai.workspace.TurWorkspaceEventBus;
import com.viglet.turing.persistence.dto.agent.TurChatSessionSlotsDto;
import com.viglet.turing.service.chatslots.TurChatSlotSseRegistry;
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
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final TurNativeChatExecutor nativeChatExecutor;
    private final TurChatFlowRepository turChatFlowRepository;
    private final TurChatFlowEngineService chatFlowEngineService;
    private final TurCodeInterpreterUrlSigner urlSigner;
    private final TurAgentWorkspace agentWorkspace;
    private final TurWorkspaceEventBus workspaceEventBus;
    private final TurChatSlotSseRegistry slotSseRegistry;
    private final com.viglet.turing.genai.safety.TurModerationService moderationService;

    public TurAIAgentChatAPI(TurAIAgentRepository turAIAgentRepository,
            TurAIAgentRepositoryPort turAIAgentRepositoryPort,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMInstanceRepositoryPort turLLMInstanceRepositoryPort,
            TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurAgentChatExecutor agentChatExecutor,
            TurNativeChatExecutor nativeChatExecutor,
            TurChatFlowRepository turChatFlowRepository,
            TurChatFlowEngineService chatFlowEngineService,
            TurCodeInterpreterUrlSigner urlSigner,
            TurAgentWorkspace agentWorkspace,
            TurWorkspaceEventBus workspaceEventBus,
            TurChatSlotSseRegistry slotSseRegistry,
            com.viglet.turing.genai.safety.TurModerationService moderationService) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentRepositoryPort = turAIAgentRepositoryPort;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMInstanceRepositoryPort = turLLMInstanceRepositoryPort;
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.agentChatExecutor = agentChatExecutor;
        this.nativeChatExecutor = nativeChatExecutor;
        this.turChatFlowRepository = turChatFlowRepository;
        this.chatFlowEngineService = chatFlowEngineService;
        this.urlSigner = urlSigner;
        this.agentWorkspace = agentWorkspace;
        this.workspaceEventBus = workspaceEventBus;
        this.slotSseRegistry = slotSseRegistry;
        this.moderationService = moderationService;
    }

    public record AgentChatRequest(
            String llmInstanceId,
            List<ChatMessageItem> messages,
            String conversationId,
            String flowId,
            String forcedVariant,
            String selectedSkillId) {
        /** Back-compat constructor (pre-T73): defaults {@code forcedVariant} to null. */
        public AgentChatRequest(String llmInstanceId, List<ChatMessageItem> messages,
                String conversationId, String flowId) {
            this(llmInstanceId, messages, conversationId, flowId, null, null);
        }

        /** Back-compat constructor (pre-T325): defaults {@code selectedSkillId} to null. */
        public AgentChatRequest(String llmInstanceId, List<ChatMessageItem> messages,
                String conversationId, String flowId, String forcedVariant) {
            this(llmInstanceId, messages, conversationId, flowId, forcedVariant, null);
        }
    }

    public record ChatMessageItem(String role, String content) {
    }

    public record ChatResponse(String role, String content, String type) {
        /** Backwards-compatible constructor: defaults {@code type} to {@code "token"}. */
        public ChatResponse(String role, String content) {
            this(role, content, "token");
        }
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatResponse> chat(
            @PathVariable String agentId,
            @org.springframework.web.bind.annotation.RequestBody AgentChatRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return doChat(agentId, request, null, httpRequest, response);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Flux<ChatResponse> chatMultipart(
            @PathVariable String agentId,
            @RequestPart("request") AgentChatRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        return doChat(agentId, request, files, httpRequest, response);
    }

    private Flux<ChatResponse> doChat(String agentId, AgentChatRequest request,
            List<MultipartFile> files, HttpServletRequest httpRequest, HttpServletResponse response) {
        TurAIAgent agent = turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("AI Agent not found: " + agentId));

        if (agent.getEnabled() != 1) {
            throw new IllegalArgumentException("AI Agent is disabled: " + agentId);
        }

        String llmInstanceId = request.llmInstanceId();
        TurLLMInstance turLLMInstance;
        if (llmInstanceId == null || llmInstanceId.isBlank()) {
            // No explicit LLM in the request: fall back to the agent's own
            // configuration. The agent owns a set of allowed LLM instances; when
            // the caller doesn't pick one, use the agent's default. Sorting by id
            // keeps the choice deterministic across calls (the relation is an
            // unordered Set). This lets public/headless clients (e.g. viglet.com)
            // depend only on the agentId.
            turLLMInstance = agent.getLlmInstances().stream()
                    .min(Comparator.comparing(TurLLMInstance::getId))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "AI Agent '" + agentId + "' has no LLM instance configured"));
        } else {
            turLLMInstance = turLLMInstanceRepository.findById(llmInstanceId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "LLM instance not found: " + llmInstanceId));

            boolean llmAllowed = agent.getLlmInstances().stream()
                    .anyMatch(llm -> llm.getId().equals(llmInstanceId));
            if (!llmAllowed) {
                throw new IllegalArgumentException(
                        "LLM instance '" + llmInstanceId + "' is not configured for agent '" + agentId + "'");
            }
        }

        int fileCount = files == null ? 0 : files.size();
        log.info("[AgentChat] Agent '{}' received request with {} messages, {} files, LLM: {}",
                agent.getTitle(), request.messages().size(), fileCount, turLLMInstance.getTitle());

        bindCodeInterpreterCookie(request, httpRequest, response);

        // T182 / §X.14.b — omni-moderation pre-filter on the user's query. When
        // the latest user turn is flagged, short-circuit with a refusal SSE turn
        // instead of calling the LLM. Opt-in + fail-open (see TurModerationService).
        Flux<ChatResponse> moderationRefusal = moderationRefusalIfFlagged(agent, request, httpRequest);
        if (moderationRefusal != null) {
            return moderationRefusal;
        }

        // All the tool resolution + chat model + streaming logic is centralized
        // in TurAgentChatExecutor so the SN site AI Mode can reuse the same
        // pipeline with an augmented system prompt (for RAG context).
        List<TurAgentChatExecutor.ChatMessageItem> mapped = request.messages().stream()
                .map(m -> new TurAgentChatExecutor.ChatMessageItem(m.role(), m.content()))
                .toList();

        // F.1/F.2 / §X.2 — native provider seam. When the chosen LLM instance
        // has opted into a native capability (e.g. OpenAI Responses
        // web_search / file_search / code_interpreter), the turn is handled by
        // the vendor SDK path. Restricted to plain turns (no file attachments,
        // no chat flow) so the rich Spring AI assembly is never silently
        // bypassed; every instance without an enabled capability returns empty
        // here and falls through to the unchanged Spring AI executor below.
        boolean plainTurn = (files == null || files.isEmpty())
                && (request.flowId() == null || request.flowId().isBlank());
        Flux<ChatResponse> stream = null;
        if (plainTurn) {
            var nativeStream = nativeChatExecutor.tryExecute(agent, turLLMInstance, mapped, null,
                    request.conversationId());
            if (nativeStream.isPresent()) {
                stream = nativeStream.get()
                        .map(r -> new ChatResponse(r.role(), r.content(), r.type()));
            }
        }
        if (stream == null) {
            stream = agentChatExecutor.execute(
                            new TurAgentChatRequest(agent, turLLMInstance, mapped, null,
                                    request.conversationId(), request.flowId(), files, null),
                            request.forcedVariant(), request.selectedSkillId())
                    .map(r -> new ChatResponse(r.role(), r.content(), r.type()));
        }

        // The response is already streaming as text/event-stream, so the global
        // exception advice can no longer write a JSON ProblemDetail body when a
        // provider error fires mid-stream — the stream just breaks and the UI
        // renders a blank assistant reply. Convert the error to a readable
        // assistant turn so the visitor sees *why* the turn failed (e.g. an
        // OpenAI "organization must be verified" 403) instead of nothing.
        String agentTitle = agent.getTitle();
        java.util.Locale locale = httpRequest.getLocale();
        return stream.onErrorResume(err -> {
            log.error("[AgentChat] Agent '{}' stream failed: {}", agentTitle, err.getMessage(), err);
            return Flux.just(new ChatResponse("assistant",
                    TurChatProviderErrorMapper.toUserMessage(err, locale), "token"));
        });
    }

    /**
     * Cookie-binding for {@code /api/v2/code-interpreter/} signed URLs. Path-
     * scoped so the cookie never travels to unrelated endpoints, and HttpOnly so
     * JavaScript can't read it (defense against XSS-based cookie theft).
     * SameSite=Lax keeps the cookie attached on top-level navigations (the
     * visitor clicking a download link in the chat transcript) while blocking it
     * on cross-origin AJAX from a different site — exactly the boundary we want.
     */
    private void bindCodeInterpreterCookie(AgentChatRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        if (!urlSigner.isCookieBound() || request.conversationId() == null
                || request.conversationId().isBlank()) {
            return;
        }
        Cookie cookie = new Cookie(TurCodeInterpreterUrlSigner.CONV_COOKIE, request.conversationId());
        cookie.setHttpOnly(true);
        // Secure only when the (possibly proxy-forwarded) request arrived over
        // HTTPS, so the cookie still works on plain-HTTP/dev deployments while
        // being TLS-only in production.
        cookie.setSecure(httpRequest.isSecure());
        cookie.setPath("/api/v2/code-interpreter");
        cookie.setMaxAge((int) Math.min(Integer.MAX_VALUE, urlSigner.getTtlSeconds()));
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    /**
     * T182 / §X.14.b — when moderation is enabled and the latest user message is
     * flagged, return a single refusal SSE turn; otherwise {@code null} so the
     * caller proceeds with the normal chat pipeline.
     */
    private Flux<ChatResponse> moderationRefusalIfFlagged(TurAIAgent agent,
            AgentChatRequest request, HttpServletRequest httpRequest) {
        if (!moderationService.isEnabled()) {
            return null;
        }
        var verdict = moderationService.moderate(lastUserMessage(request.messages()));
        if (!verdict.flagged()) {
            return null;
        }
        log.info("[AgentChat] Agent '{}' query blocked by moderation (categories: {})",
                agent.getTitle(), verdict.categories());
        return Flux.just(new ChatResponse("assistant",
                moderationRefusalMessage(httpRequest.getLocale())));
    }

    /** The most recent {@code user}-role message in the request, or empty string. */
    private static String lastUserMessage(List<ChatMessageItem> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageItem item = messages.get(i);
            if (item != null && "user".equalsIgnoreCase(item.role()) && item.content() != null) {
                return item.content();
            }
        }
        return "";
    }

    /** A localized refusal shown when a query is blocked by moderation (pt/es/en). */
    private static String moderationRefusalMessage(java.util.Locale locale) {
        String lang = locale == null ? "en" : locale.getLanguage();
        return switch (lang) {
            case "pt" -> "Não posso ajudar com essa solicitação.";
            case "es" -> "No puedo ayudar con esa solicitud.";
            default -> "I can't help with that request.";
        };
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

    /**
     * Public, unauthenticated read of the slots captured during the
     * conversation, returned as a single flat map at the JSON root. Lets
     * the SDK pick up slot values the visitor has already filled and
     * reflect them elsewhere on the page without re-asking. The
     * {@code conversationId} comes from the SDK's {@code TUR_SESSION}
     * cookie — same id the chat-flow engine keys on.
     *
     * @since 2026.2.7
     */
    @GetMapping("/chat-slots")
    public TurChatSessionSlotsDto chatSlots(@PathVariable String agentId,
            @RequestParam String conversationId) {
        return chatFlowEngineService.listSlotsForConversation(conversationId);
    }

    /**
     * T113 — agent-scoped variant of the workspace artifact stream. Same
     * contract as the site-scoped {@code /api/sn/{site}/chat/workspace/stream}
     * (one server-sent event per blob {@code put}/{@code delete}, metadata
     * only), but the {@code agentId} rides in the path instead of a query
     * param — so the admin chat console (which is agent-scoped and has no
     * "site") can render the live "files this agent built for you" sidebar.
     *
     * <p>The initial snapshot back-fill always runs here (the path always
     * carries the agent), then the bus relays live mutations. Same 25s comment
     * heartbeat, T90 refcounting on the {@code WORKSPACE} channel, and
     * single-node caveat as the slot streams.
     *
     * @since 2026.3.1
     */
    @GetMapping(value = "/workspace/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<TurWorkspaceEvent>> streamWorkspace(
            @PathVariable String agentId,
            @RequestParam String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return Flux.empty();
        }
        Flux<TurWorkspaceEvent> initial =
                Flux.fromIterable(agentWorkspace.list(agentId, conversationId, null))
                        .map(entry -> TurWorkspaceEvent.put(conversationId, entry.key(),
                                entry.contentType(), entry.sizeBytes(), entry.signedUrl()));
        Flux<org.springframework.http.codec.ServerSentEvent<TurWorkspaceEvent>> data =
                Flux.concat(initial, workspaceEventBus.subscribe(conversationId))
                        .map(event -> org.springframework.http.codec.ServerSentEvent
                                .<TurWorkspaceEvent>builder(event).build());
        Flux<org.springframework.http.codec.ServerSentEvent<TurWorkspaceEvent>> heartbeats =
                Flux.interval(java.time.Duration.ofSeconds(25))
                        .map(tick -> org.springframework.http.codec.ServerSentEvent
                                .<TurWorkspaceEvent>builder().comment("heartbeat").build());
        return Flux.merge(data, heartbeats)
                .doOnSubscribe(s -> slotSseRegistry.acquire(conversationId,
                        TurChatSlotSseRegistry.Mode.WORKSPACE))
                .doFinally(sig -> slotSseRegistry.release(conversationId,
                        TurChatSlotSseRegistry.Mode.WORKSPACE));
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
