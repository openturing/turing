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
    private TurGenAiLlmProvider provider;

    private TurLLMChatAPI api;

    @BeforeEach
    void setUp() {
        api = new TurLLMChatAPI(
                turLLMInstanceRepository, llmProviderFactory, llmModelFactory, turSecretCryptoService,
                webCrawlerToolService, weatherToolService, financeToolService,
                codeInterpreterToolService, imageSearchToolService,
                dateTimeToolService, ragSearchToolService,
                tokenUsageService, toolCallbackPipeline,
                loggingToolService, integrationMonitoringToolService,
                systemInfoToolService, iconifyToolService);
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
        instance.setContextWindow(64000);

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
        instance.setContextWindow(null);

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
        when(turLLMInstanceRepository.findById("missing")).thenReturn(Optional.empty());

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

    // --- buildMessages with files ---

    @Test
    void shouldBuildMessagesAttachingFilesToLastUserMessage() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "First"),
                new TurLLMChatAPI.ChatMessageItem("user", "Analyze this"));

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "test.txt", "text/plain", "Some text content".getBytes());

        @SuppressWarnings("unchecked")
        List<org.springframework.ai.chat.messages.Message> messages =
                (List<org.springframework.ai.chat.messages.Message>) method.invoke(api, items, List.of(file));

        assertThat(messages).hasSize(2);
        // First message should be plain UserMessage
        assertThat(messages.get(0)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
        // Last message should include file text
        assertThat(messages.get(1)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
    }

    @Test
    void shouldBuildMessagesWithNullFiles() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "Hello"));

        @SuppressWarnings("unchecked")
        List<org.springframework.ai.chat.messages.Message> messages =
                (List<org.springframework.ai.chat.messages.Message>) method.invoke(api, items, null);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
    }

    @Test
    void shouldBuildMessagesWithEmptyFiles() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildMessages", List.class, List.class);
        method.setAccessible(true);

        List<TurLLMChatAPI.ChatMessageItem> items = List.of(
                new TurLLMChatAPI.ChatMessageItem("user", "Hello"));

        @SuppressWarnings("unchecked")
        List<org.springframework.ai.chat.messages.Message> messages =
                (List<org.springframework.ai.chat.messages.Message>) method.invoke(api, items, List.of());

        assertThat(messages).hasSize(1);
    }

    // --- extractTextFromFile Tests ---

    @Test
    void shouldExtractTextFromPlainTextFile() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("extractTextFromFile",
                org.springframework.web.multipart.MultipartFile.class);
        method.setAccessible(true);

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "test.txt", "text/plain", "Hello World".getBytes());

        String result = (String) method.invoke(api, file);

        assertThat(result).contains("Hello World");
    }

    @Test
    void shouldReturnEmptyStringForEmptyFile() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("extractTextFromFile",
                org.springframework.web.multipart.MultipartFile.class);
        method.setAccessible(true);

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "empty.txt", "text/plain", new byte[0]);

        String result = (String) method.invoke(api, file);

        assertThat(result).isEmpty();
    }

    // --- Additional coverage: buildUserMessageWithFiles ---

    @Test
    void shouldBuildUserMessageWithImageFile() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildUserMessageWithFiles",
                String.class, List.class);
        method.setAccessible(true);

        org.springframework.mock.web.MockMultipartFile imageFile =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "photo.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        UserMessage result = (UserMessage) method.invoke(api, "Describe this image", List.of(imageFile));

        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Describe this image");
    }

    @Test
    void shouldBuildUserMessageWithTextFileOnly() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildUserMessageWithFiles",
                String.class, List.class);
        method.setAccessible(true);

        org.springframework.mock.web.MockMultipartFile textFile =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "doc.txt", "text/plain", "Important document content".getBytes());

        UserMessage result = (UserMessage) method.invoke(api, "Analyze this", List.of(textFile));

        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Analyze this");
        assertThat(result.getText()).contains("Important document content");
    }

    @Test
    void shouldBuildUserMessageWithNullContentType() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildUserMessageWithFiles",
                String.class, List.class);
        method.setAccessible(true);

        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "data.bin", null, "binary content".getBytes());

        UserMessage result = (UserMessage) method.invoke(api, "Check this", List.of(file));

        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Check this");
    }

    @Test
    void shouldBuildUserMessageWithMultipleFiles() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildUserMessageWithFiles",
                String.class, List.class);
        method.setAccessible(true);

        org.springframework.mock.web.MockMultipartFile textFile =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "readme.txt", "text/plain", "README content".getBytes());
        org.springframework.mock.web.MockMultipartFile imageFile =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "chart.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        UserMessage result = (UserMessage) method.invoke(api, "Analyze both", List.of(textFile, imageFile));

        assertThat(result).isNotNull();
        assertThat(result.getText()).contains("Analyze both");
        assertThat(result.getText()).contains("README content");
    }

    @Test
    void shouldExtractTextFromHtmlFile() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("extractTextFromFile",
                org.springframework.web.multipart.MultipartFile.class);
        method.setAccessible(true);

        String html = "<html><body><h1>Title</h1><p>Content paragraph</p></body></html>";
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "page.html", "text/html", html.getBytes());

        String result = (String) method.invoke(api, file);

        assertThat(result).contains("Title");
        assertThat(result).contains("Content paragraph");
    }

    @Test
    void shouldBuildMessagesWithAssistantLastMessage() throws Exception {
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

        // Last message is assistant, so files should NOT be attached to it
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
    }

    @Test
    void shouldBuildUserMessageWithBlankExtractedContent() throws Exception {
        Method method = TurLLMChatAPI.class.getDeclaredMethod("buildUserMessageWithFiles",
                String.class, List.class);
        method.setAccessible(true);

        // Empty content file (binary with no extractable text)
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile(
                        "files", "empty.bin", "application/octet-stream", new byte[]{0, 1, 2});

        UserMessage result = (UserMessage) method.invoke(api, "What is this?", List.of(file));

        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("What is this?");
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
