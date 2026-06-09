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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.genai.TurChatAttachmentService;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurCodeInterpreterToolService;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.genai.tool.TurDateTimeToolService;
import com.viglet.turing.genai.tool.TurFinanceToolService;
import com.viglet.turing.genai.tool.TurImageSearchToolService;
import com.viglet.turing.genai.tool.TurWeatherToolService;
import com.viglet.turing.genai.tool.TurRagSearchToolService;
import com.viglet.turing.genai.tool.TurIconifyToolService;
import com.viglet.turing.genai.tool.TurIntegrationMonitoringToolService;
import com.viglet.turing.genai.tool.TurLoggingToolService;
import com.viglet.turing.genai.tool.TurSystemInfoToolService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.genai.tool.TurWebCrawlerToolService;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

@ExtendWith(MockitoExtension.class)
class TurLLMChatAPITest {

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
    private TurWebCrawlerToolService webCrawlerToolService;
    @Mock
    private TurWeatherToolService weatherToolService;
    @Mock
    private TurFinanceToolService financeToolService;
    @Mock
    private TurCodeInterpreterToolService codeInterpreterToolService;
    @Mock
    private TurImageSearchToolService imageSearchToolService;
    @Mock
    private TurDateTimeToolService dateTimeToolService;
    @Mock
    private TurRagSearchToolService ragSearchToolService;
    @Mock
    private TurLLMTokenUsageService tokenUsageService;
    @Mock
    private TurToolCallbackPipeline toolCallbackPipeline;
    @Mock
    private TurLoggingToolService loggingToolService;
    @Mock
    private TurIntegrationMonitoringToolService integrationMonitoringToolService;
    @Mock
    private TurSystemInfoToolService systemInfoToolService;
    @Mock
    private TurIconifyToolService iconifyToolService;
    @Mock
    private TurChatAttachmentService chatAttachmentService;
    @Mock
    private TurGenAiLlmProvider provider;

    private TurLLMChatAPI api;

    @BeforeEach
    void setUp() {
        api = new TurLLMChatAPI(
                turLLMInstanceRepository, turLLMInstanceRepositoryPort,
                llmProviderFactory, llmModelFactory, turSecretCryptoService,
                webCrawlerToolService, weatherToolService, financeToolService,
                codeInterpreterToolService, imageSearchToolService,
                dateTimeToolService, ragSearchToolService,
                tokenUsageService, toolCallbackPipeline,
                loggingToolService, integrationMonitoringToolService,
                systemInfoToolService, iconifyToolService,
                chatAttachmentService,
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
        var item = new TurLLMChatAPI.ChatMessageItem("user", "Hello");
        var request = new TurLLMChatAPI.ChatRequest(List.of(item));

        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().getFirst().role()).isEqualTo("user");
        assertThat(request.messages().getFirst().content()).isEqualTo("Hello");
    }

    @Test
    void shouldCreateChatResponse() {
        var response = new TurLLMChatAPI.ChatResponse("assistant", "Hi there!");

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.content()).isEqualTo("Hi there!");
    }

    @Test
    void shouldCreateContextInfoResponse() {
        var info = new TurLLMChatAPI.ContextInfoResponse(128000, "config");

        assertThat(info.contextWindow()).isEqualTo(128000);
        assertThat(info.source()).isEqualTo("config");
    }

    // --- buildMessages Tests ---

    @Test
    void shouldBuildMessagesWithUserAndAssistant() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "What is AI?"),
                new TurLLMChatAPI.ChatMessageItem("assistant", "AI is..."),
                new TurLLMChatAPI.ChatMessageItem("user", "Tell me more"));

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) method.invoke(api, items, null);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
    }

    @Test
    void shouldBuildMessagesWithEmptyList() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) method.invoke(api, List.of(), null);

        assertThat(messages).isEmpty();
    }

    @Test
    void shouldBuildMessagesWithSingleUser() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "Hello"));

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) method.invoke(api, items, null);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst()).isInstanceOf(UserMessage.class);
    }

    // --- contextInfo Tests ---

    @Test
    void shouldReturnProviderContextWindow() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setApiKeyEncrypted("encrypted");

        when(turLLMInstanceRepositoryPort.findById("inst-1"))
                .thenReturn(Optional.of(llmDomain("inst-1", null)));
        when(turLLMInstanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("encrypted")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.of(200000));

        TurLLMChatAPI.ContextInfoResponse result = api.contextInfo("inst-1");

        assertThat(result.contextWindow()).isEqualTo(200000);
        assertThat(result.source()).isEqualTo("provider");
    }

    @Test
    void shouldReturnStoredContextWindow() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setApiKeyEncrypted("encrypted");

        when(turLLMInstanceRepositoryPort.findById("inst-1"))
                .thenReturn(Optional.of(llmDomain("inst-1", 64000)));
        when(turLLMInstanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("encrypted")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.empty());

        TurLLMChatAPI.ContextInfoResponse result = api.contextInfo("inst-1");

        assertThat(result.contextWindow()).isEqualTo(64000);
        assertThat(result.source()).isEqualTo("config");
    }

    @Test
    void shouldReturnDefaultContextWindowWhenNullStored() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("inst-1");
        instance.setApiKeyEncrypted("encrypted");

        when(turLLMInstanceRepositoryPort.findById("inst-1"))
                .thenReturn(Optional.of(llmDomain("inst-1", null)));
        when(turLLMInstanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("encrypted")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.empty());

        TurLLMChatAPI.ContextInfoResponse result = api.contextInfo("inst-1");

        assertThat(result.contextWindow()).isEqualTo(128000);
        assertThat(result.source()).isEqualTo("config");
    }

    @Test
    void shouldThrowWhenInstanceNotFoundInContextInfo() {
        when(turLLMInstanceRepositoryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.contextInfo("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    // --- resolveUsername Tests ---

    @Test
    void shouldReturnAnonymousWhenNoAuth() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("resolveUsername");
        method.setAccessible(true);

        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        String username = (String) method.invoke(api);

        assertThat(username).isEqualTo("anonymous");
    }

    @Test
    void shouldReturnAuthenticatedUsername() throws Exception {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "testuser", "password");
        var context = org.mockito.Mockito.mock(org.springframework.security.core.context.SecurityContext.class);
        org.mockito.Mockito.when(context.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        Method method = TurLLMChatAPI.class.getDeclaredMethod("resolveUsername");
        method.setAccessible(true);

        String username = (String) method.invoke(api);

        assertThat(username).isEqualTo("testuser");

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    // --- chatJson / doChat Tests ---

    @Test
    void shouldThrowWhenInstanceNotFoundInChat() {
        when(turLLMInstanceRepository.findById("missing")).thenReturn(Optional.empty());

        var request = new TurLLMChatAPI.ChatRequest(
                List.of(new TurLLMChatAPI.ChatMessageItem("user", "Hello")));

        assertThatThrownBy(() -> api.chatJson("missing", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    // File-handling helpers were extracted to TurChatAttachmentService (T221a);
    // their behaviour is now covered by TurChatAttachmentServiceTest. The
    // buildMessages-with-files test below stays here to pin the delegation
    // (mocked attachment service called for the last user msg only).

    @Test
    void shouldDelegateToAttachmentServiceForLastUserWhenFilesPresent() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "First"),
                new TurLLMChatAPI.ChatMessageItem("user", "Analyze this"));

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "test.txt", "text/plain", "Some text content".getBytes());

        UserMessage delegateResult = new UserMessage("Analyze this + attached");
        when(chatAttachmentService.buildUserMessageWithFiles("Analyze this", List.of(file)))
                .thenReturn(delegateResult);

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) method.invoke(api, items, List.of(file));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(0)).extracting("text").isEqualTo("First");
        assertThat(messages.get(1)).isSameAs(delegateResult);
    }

    @Test
    void shouldNotInvokeAttachmentServiceWhenLastMessageIsAssistant() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "Hello"),
                new TurLLMChatAPI.ChatMessageItem("assistant", "Hi there!"));

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "test.txt", "text/plain", "File content".getBytes());

        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) method.invoke(api, items, List.of(file));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        // attachmentService is never stubbed here — Mockito strict would fail if it was called
    }

    // --- chat record equality ---

    @Test
    void chatMessageItemShouldHaveCorrectEquality() {
        var item1 = new TurLLMChatAPI.ChatMessageItem("user", "Hello");
        var item2 = new TurLLMChatAPI.ChatMessageItem("user", "Hello");

        assertThat(item1).isEqualTo(item2).hasSameHashCodeAs(item2);
    }

    @Test
    void chatResponseShouldHaveCorrectEquality() {
        var r1 = new TurLLMChatAPI.ChatResponse("assistant", "Hi");
        var r2 = new TurLLMChatAPI.ChatResponse("assistant", "Hi");

        assertThat(r1).isEqualTo(r2).hasSameHashCodeAs(r2);
    }
}
