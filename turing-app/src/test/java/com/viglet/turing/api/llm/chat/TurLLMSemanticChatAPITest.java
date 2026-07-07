package com.viglet.turing.api.llm.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurDslToolService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

@ExtendWith(MockitoExtension.class)
class TurLLMSemanticChatAPITest {

    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock
    private TurLLMInstanceRepositoryPort turLLMInstanceRepositoryPort;
    @Mock
    private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock
    private TurLlmModelFactory llmModelFactory;
    @Mock
    private TurSecretCryptoService turSecretCryptoService;
    @Mock
    private TurDslToolService dslToolService;
    @Mock
    private com.viglet.turing.genai.catalog.TurCatalogCopilotToolService catalogCopilotToolService;
    @Mock
    private com.viglet.turing.genai.catalog.TurRankingExplainToolService rankingExplainToolService;
    @Mock
    private TurMcpToolCallbackService mcpToolCallbackService;
    @Mock
    private TurToolCallbackPipeline toolCallbackPipeline;
    @Mock
    private TurLLMTokenUsageService tokenUsageService;
    @Mock
    private TurGenAiLlmProvider provider;

    private TurLLMSemanticChatAPI api;

    @BeforeEach
    void setUp() {
        api = new TurLLMSemanticChatAPI(
                turLLMInstanceRepository, turLLMInstanceRepositoryPort,
                llmProviderFactory, llmModelFactory, turSecretCryptoService,
                dslToolService, catalogCopilotToolService, rankingExplainToolService,
                mcpToolCallbackService, toolCallbackPipeline, tokenUsageService,
                new com.viglet.turing.genai.TurToolExecutionLoop());
    }

    private static TurLLMInstanceDomain llmDomain(String id, Integer contextWindow) {
        return new TurLLMInstanceDomain(id, "LLM " + id, null, null, 1, null,
                "openai", "gpt-4o-mini", null, null, null, null, null, null, null,
                null, null, null, null, contextWindow, null, false);
    }

    // --- Record Tests ---

    @Test
    void shouldCreateChatRequest() {
        var items = List.of(new TurLLMSemanticChatAPI.ChatMessageItem("user", "search for AI"));
        var request = new TurLLMSemanticChatAPI.ChatRequest(items);

        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().getFirst().role()).isEqualTo("user");
    }

    @Test
    void shouldCreateChatResponse() {
        var response = new TurLLMSemanticChatAPI.ChatResponse("assistant", "Found results");

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.content()).isEqualTo("Found results");
    }

    @Test
    void shouldCreateChatMessageItem() {
        var item = new TurLLMSemanticChatAPI.ChatMessageItem("user", "query");

        assertThat(item.role()).isEqualTo("user");
        assertThat(item.content()).isEqualTo("query");
    }

    // --- contextInfo Tests ---

    @Test
    void shouldReturnProviderContextWindow() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setApiKeyEncrypted("enc-key");

        when(turLLMInstanceRepositoryPort.findById("inst-1"))
                .thenReturn(Optional.of(llmDomain("inst-1", null)));
        when(turLLMInstanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("enc-key")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.of(32000));

        TurLLMChatAPI.ContextInfoResponse result = api.contextInfo("inst-1");

        assertThat(result.contextWindow()).isEqualTo(32000);
        assertThat(result.source()).isEqualTo("provider");
    }

    @Test
    void shouldFallbackToStoredContextWindow() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setApiKeyEncrypted("enc-key");

        when(turLLMInstanceRepositoryPort.findById("inst-1"))
                .thenReturn(Optional.of(llmDomain("inst-1", 50000)));
        when(turLLMInstanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("enc-key")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.empty());

        TurLLMChatAPI.ContextInfoResponse result = api.contextInfo("inst-1");

        assertThat(result.contextWindow()).isEqualTo(50000);
        assertThat(result.source()).isEqualTo("config");
    }

    @Test
    void shouldFallbackToDefaultContextWindow() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setApiKeyEncrypted("enc-key");

        when(turLLMInstanceRepositoryPort.findById("inst-1"))
                .thenReturn(Optional.of(llmDomain("inst-1", null)));
        when(turLLMInstanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("enc-key")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.empty());

        TurLLMChatAPI.ContextInfoResponse result = api.contextInfo("inst-1");

        assertThat(result.contextWindow()).isEqualTo(128000);
    }

    @Test
    void shouldThrowWhenInstanceNotFound() {
        when(turLLMInstanceRepositoryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.contextInfo("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    // --- resolveUsername ---

    @Test
    void shouldReturnAnonymousWhenNoAuth() throws Exception {
        Method method = TurLLMSemanticChatAPI.class.getDeclaredMethod("resolveUsername");
        method.setAccessible(true);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        String username = (String) method.invoke(api);

        assertThat(username).isEqualTo("anonymous");
    }

    @Test
    void shouldReturnUsernameWhenAuthenticated() throws Exception {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "testuser", "password");
        var context = org.mockito.Mockito.mock(org.springframework.security.core.context.SecurityContext.class);
        org.mockito.Mockito.when(context.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        Method method = TurLLMSemanticChatAPI.class.getDeclaredMethod("resolveUsername");
        method.setAccessible(true);

        String username = (String) method.invoke(api);
        assertThat(username).isEqualTo("testuser");

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // --- chat() validation tests ---

    @Test
    void chatShouldThrowWhenInstanceNotFound() {
        when(turLLMInstanceRepository.findById("missing")).thenReturn(Optional.empty());

        var request = new TurLLMSemanticChatAPI.ChatRequest(
                List.of(new TurLLMSemanticChatAPI.ChatMessageItem("user", "Hello")));

        assertThatThrownBy(() -> api.chat("missing", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    // --- Record equality ---

    @Test
    void chatRequestShouldSupportEquality() {
        var items = List.of(new TurLLMSemanticChatAPI.ChatMessageItem("user", "test"));
        var r1 = new TurLLMSemanticChatAPI.ChatRequest(items);
        var r2 = new TurLLMSemanticChatAPI.ChatRequest(items);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void chatResponseShouldSupportEquality() {
        var r1 = new TurLLMSemanticChatAPI.ChatResponse("assistant", "reply");
        var r2 = new TurLLMSemanticChatAPI.ChatResponse("assistant", "reply");

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void chatMessageItemShouldSupportToString() {
        var item = new TurLLMSemanticChatAPI.ChatMessageItem("user", "hello");

        assertThat(item.toString()).contains("user");
        assertThat(item.toString()).contains("hello");
    }
}
