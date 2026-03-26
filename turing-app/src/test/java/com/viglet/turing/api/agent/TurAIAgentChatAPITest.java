package com.viglet.turing.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Tests for TurAIAgentChatAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurAIAgentChatAPITest {

    @Mock
    private TurAIAgentRepository turAIAgentRepository;
    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock
    private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock
    private TurSecretCryptoService turSecretCryptoService;
    @Mock
    private TurNativeToolService nativeToolService;
    @Mock
    private TurMcpToolCallbackService mcpToolCallbackService;
    @Mock
    private TurLLMTokenUsageService tokenUsageService;
    @Mock
    private TurGenAiLlmProvider provider;

    private TurAIAgentChatAPI api;

    @BeforeEach
    void setUp() {
        api = new TurAIAgentChatAPI(
                turAIAgentRepository, turLLMInstanceRepository, llmProviderFactory,
                turSecretCryptoService, nativeToolService, mcpToolCallbackService,
                tokenUsageService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- Record Tests ---

    @Test
    void shouldCreateAgentChatRequest() {
        var item = new TurAIAgentChatAPI.ChatMessageItem("user", "Hello");
        var request = new TurAIAgentChatAPI.AgentChatRequest("llm-1", List.of(item));

        assertThat(request.llmInstanceId()).isEqualTo("llm-1");
        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().getFirst().role()).isEqualTo("user");
        assertThat(request.messages().getFirst().content()).isEqualTo("Hello");
    }

    @Test
    void shouldCreateChatResponse() {
        var response = new TurAIAgentChatAPI.ChatResponse("assistant", "Hi there!");

        assertThat(response.role()).isEqualTo("assistant");
        assertThat(response.content()).isEqualTo("Hi there!");
    }

    @Test
    void shouldCreateChatMessageItem() {
        var item = new TurAIAgentChatAPI.ChatMessageItem("user", "query");

        assertThat(item.role()).isEqualTo("user");
        assertThat(item.content()).isEqualTo("query");
    }

    // --- chat() validation tests ---

    @Test
    void chatShouldThrowWhenAgentNotFound() {
        when(turAIAgentRepository.findById("missing")).thenReturn(Optional.empty());

        var request = new TurAIAgentChatAPI.AgentChatRequest("llm-1",
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")));

        assertThatThrownBy(() -> api.chat("missing", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI Agent not found");
    }

    @Test
    void chatShouldThrowWhenAgentDisabled() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setEnabled(0);
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        var request = new TurAIAgentChatAPI.AgentChatRequest("llm-1",
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")));

        assertThatThrownBy(() -> api.chat("agent-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI Agent is disabled");
    }

    @Test
    void chatShouldThrowWhenLlmInstanceNotFound() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setEnabled(1);
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("missing-llm")).thenReturn(Optional.empty());

        var request = new TurAIAgentChatAPI.AgentChatRequest("missing-llm",
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")));

        assertThatThrownBy(() -> api.chat("agent-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    @Test
    void chatShouldThrowWhenLlmNotAllowedForAgent() {
        TurLLMInstance llmInstance = new TurLLMInstance();
        llmInstance.setId("llm-1");

        TurLLMInstance otherLlm = new TurLLMInstance();
        otherLlm.setId("llm-other");

        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setEnabled(1);
        agent.setLlmInstances(Set.of(otherLlm));

        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(llmInstance));

        var request = new TurAIAgentChatAPI.AgentChatRequest("llm-1",
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")));

        assertThatThrownBy(() -> api.chat("agent-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not configured for agent");
    }

    // --- contextInfo Tests ---

    @Test
    void contextInfoShouldReturnProviderContextWindow() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted("enc");

        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("enc")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.of(200000));

        var result = api.contextInfo("agent-1", "llm-1");

        assertThat(result.contextWindow()).isEqualTo(200000);
        assertThat(result.source()).isEqualTo("provider");
    }

    @Test
    void contextInfoShouldFallbackToStoredContextWindow() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted("enc");
        instance.setContextWindow(64000);

        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("enc")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.empty());

        var result = api.contextInfo("agent-1", "llm-1");

        assertThat(result.contextWindow()).isEqualTo(64000);
        assertThat(result.source()).isEqualTo("config");
    }

    @Test
    void contextInfoShouldReturnDefaultWhenNullContextWindow() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted("enc");
        instance.setContextWindow(null);

        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt("enc")).thenReturn("key");
        when(provider.fetchContextWindow(instance, "key")).thenReturn(OptionalInt.empty());

        var result = api.contextInfo("agent-1", "llm-1");

        assertThat(result.contextWindow()).isEqualTo(128000);
        assertThat(result.source()).isEqualTo("config");
    }

    @Test
    void contextInfoShouldThrowWhenAgentNotFound() {
        when(turAIAgentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.contextInfo("missing", "llm-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI Agent not found");
    }

    @Test
    void contextInfoShouldThrowWhenLlmNotFound() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.contextInfo("agent-1", "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    // --- parseNativeTools Tests ---

    @Test
    void parseNativeToolsShouldReturnEmptySetForNull() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, (String) null);
        assertThat(result).isEmpty();
    }

    @Test
    void parseNativeToolsShouldReturnEmptySetForBlank() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, "   ");
        assertThat(result).isEmpty();
    }

    @Test
    void parseNativeToolsShouldParseCommaSeparated() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, "tool1, tool2, tool3");
        assertThat(result).containsExactlyInAnyOrder("tool1", "tool2", "tool3");
    }

    @Test
    void parseNativeToolsShouldTrimValues() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, " tool1 , tool2 ");
        assertThat(result).containsExactlyInAnyOrder("tool1", "tool2");
    }

    @Test
    void parseNativeToolsShouldIgnoreEmptyEntries() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, "tool1,,, tool2,");
        assertThat(result).containsExactlyInAnyOrder("tool1", "tool2");
    }

    // --- resolveUsername Tests ---

    @Test
    void resolveUsernameShouldReturnAnonymousWhenNoAuth() throws Exception {
        SecurityContextHolder.clearContext();
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("resolveUsername");
        method.setAccessible(true);

        String username = (String) method.invoke(api);

        assertThat(username).isEqualTo("anonymous");
    }

    @Test
    void resolveUsernameShouldReturnUsernameWhenAuthenticated() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken("testuser", "password");
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("resolveUsername");
        method.setAccessible(true);

        String username = (String) method.invoke(api);

        assertThat(username).isEqualTo("testuser");
    }

    // --- Additional coverage: parseNativeTools edge cases ---

    @Test
    void parseNativeToolsShouldHandleSingleTool() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, "weather");
        assertThat(result).containsExactly("weather");
    }

    @Test
    void parseNativeToolsShouldHandleTrailingComma() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, "tool1,tool2,");
        assertThat(result).containsExactlyInAnyOrder("tool1", "tool2");
    }

    @Test
    void parseNativeToolsShouldHandleLeadingComma() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, ",tool1,tool2");
        assertThat(result).containsExactlyInAnyOrder("tool1", "tool2");
    }

    @Test
    void parseNativeToolsShouldDeduplicateEntries() throws Exception {
        Method method = TurAIAgentChatAPI.class.getDeclaredMethod("parseNativeTools", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(null, "tool1,tool1,tool2");
        assertThat(result).containsExactlyInAnyOrder("tool1", "tool2");
    }

    // --- contextInfo edge cases ---

    @Test
    void contextInfoShouldWorkWithNullApiKeyEncrypted() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted(null);
        instance.setContextWindow(32000);

        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(turLLMInstanceRepository.findById("llm-1")).thenReturn(Optional.of(instance));
        when(llmProviderFactory.getProvider(instance)).thenReturn(provider);
        when(turSecretCryptoService.decrypt(null)).thenReturn(null);
        when(provider.fetchContextWindow(instance, null)).thenReturn(OptionalInt.empty());

        var result = api.contextInfo("agent-1", "llm-1");

        assertThat(result.contextWindow()).isEqualTo(32000);
        assertThat(result.source()).isEqualTo("config");
    }

    // --- Record tests ---

    @Test
    void agentChatRequestShouldHaveCorrectEquality() {
        var item = new TurAIAgentChatAPI.ChatMessageItem("user", "Hello");
        var r1 = new TurAIAgentChatAPI.AgentChatRequest("llm-1", List.of(item));
        var r2 = new TurAIAgentChatAPI.AgentChatRequest("llm-1", List.of(item));

        assertThat(r1).isEqualTo(r2);
        assertThat(r1).hasSameHashCodeAs(r2);
    }

    @Test
    void chatResponseShouldHaveCorrectEquality() {
        var r1 = new TurAIAgentChatAPI.ChatResponse("assistant", "Hi");
        var r2 = new TurAIAgentChatAPI.ChatResponse("assistant", "Hi");

        assertThat(r1).isEqualTo(r2);
        assertThat(r1).hasSameHashCodeAs(r2);
    }

    @Test
    void chatResponseShouldHaveCorrectInequality() {
        var r1 = new TurAIAgentChatAPI.ChatResponse("assistant", "Hi");
        var r2 = new TurAIAgentChatAPI.ChatResponse("user", "Hi");

        assertThat(r1).isNotEqualTo(r2);
    }
}
