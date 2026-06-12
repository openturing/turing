package com.viglet.turing.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.turing.domain.agent.TurAIAgentDomain;
import com.viglet.turing.domain.agent.TurAIAgentRepositoryPort;
import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.tool.TurMcpToolCallbackService;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurChatFlowRepository;
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
    private TurAIAgentRepositoryPort turAIAgentRepositoryPort;
    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock
    private TurLLMInstanceRepositoryPort turLLMInstanceRepositoryPort;
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
    @Mock
    private com.viglet.turing.genai.TurAgentChatExecutor agentChatExecutor;
    @Mock
    private com.viglet.turing.genai.nativeapi.TurNativeChatExecutor nativeChatExecutor;
    @Mock
    private TurChatFlowRepository turChatFlowRepository;
    @Mock
    private com.viglet.turing.genai.flow.TurChatFlowEngineService chatFlowEngineService;
    @Mock
    private com.viglet.turing.genai.tool.TurCodeInterpreterUrlSigner urlSigner;
    @Mock
    private com.viglet.turing.genai.workspace.TurAgentWorkspace agentWorkspace;
    @Mock
    private com.viglet.turing.genai.workspace.TurWorkspaceEventBus workspaceEventBus;
    @Mock
    private com.viglet.turing.service.chatslots.TurChatSlotSseRegistry slotSseRegistry;

    private TurAIAgentChatAPI api;

    @BeforeEach
    void setUp() {
        api = new TurAIAgentChatAPI(
                turAIAgentRepository, turAIAgentRepositoryPort,
                turLLMInstanceRepository, turLLMInstanceRepositoryPort,
                llmProviderFactory, turSecretCryptoService, agentChatExecutor,
                nativeChatExecutor,
                turChatFlowRepository, chatFlowEngineService, urlSigner,
                agentWorkspace, workspaceEventBus, slotSseRegistry);
    }

    private static TurAIAgentDomain agentDomain(String id) {
        return new TurAIAgentDomain(id, "Agent " + id, null, null, null, null, 1,
                false, null, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                null, null);
    }

    private static TurLLMInstanceDomain llmDomain(String id, Integer contextWindow) {
        return new TurLLMInstanceDomain(id, "LLM " + id, null, null, 1, null,
                "openai", "gpt-4o-mini", null, null, null, null, null, null, null,
                null, null, null, null, contextWindow, null, false);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- Record Tests ---

    @Test
    void shouldCreateAgentChatRequest() {
        var item = new TurAIAgentChatAPI.ChatMessageItem("user", "Hello");
        var request = new TurAIAgentChatAPI.AgentChatRequest("llm-1", List.of(item), "conv-1", "flow-1");

        assertThat(request.llmInstanceId()).isEqualTo("llm-1");
        assertThat(request.messages()).hasSize(1);
        assertThat(request.messages().getFirst().role()).isEqualTo("user");
        assertThat(request.messages().getFirst().content()).isEqualTo("Hello");
        assertThat(request.conversationId()).isEqualTo("conv-1");
        assertThat(request.flowId()).isEqualTo("flow-1");
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
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")), null, null);

        assertThatThrownBy(() -> api.chat("missing", request, org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class)))
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
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")), null, null);

        assertThatThrownBy(() -> api.chat("agent-1", request, org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class)))
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
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")), null, null);

        assertThatThrownBy(() -> api.chat("agent-1", request, org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class)))
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
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")), null, null);

        assertThatThrownBy(() -> api.chat("agent-1", request, org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not configured for agent");
    }

    @Test
    void chatShouldUseAgentFirstLlmWhenInstanceIdBlank() {
        // Two LLMs on the agent; with a blank request id the endpoint must pick
        // the agent's own LLM deterministically (lowest id = "llm-a"), never
        // hitting the repository for an explicit lookup.
        TurLLMInstance llmA = new TurLLMInstance();
        llmA.setId("llm-a");
        TurLLMInstance llmB = new TurLLMInstance();
        llmB.setId("llm-b");

        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setEnabled(1);
        agent.setLlmInstances(Set.of(llmA, llmB));
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(agentChatExecutor.execute(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(reactor.core.publisher.Flux.empty());

        var request = new TurAIAgentChatAPI.AgentChatRequest(null,
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")), null, null);

        var flux = api.chat("agent-1", request,
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class));
        flux.blockLast();

        var captor = org.mockito.ArgumentCaptor.forClass(TurLLMInstance.class);
        org.mockito.Mockito.verify(agentChatExecutor).execute(
                org.mockito.ArgumentMatchers.any(), captor.capture(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.nullable(String.class),
                org.mockito.ArgumentMatchers.nullable(String.class));
        assertThat(captor.getValue().getId()).isEqualTo("llm-a");
        org.mockito.Mockito.verify(turLLMInstanceRepository, org.mockito.Mockito.never())
                .findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void chatShouldThrowWhenBlankIdAndAgentHasNoLlm() {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setEnabled(1);
        // No LLM instances configured on the agent.
        when(turAIAgentRepository.findById("agent-1")).thenReturn(Optional.of(agent));

        var request = new TurAIAgentChatAPI.AgentChatRequest("",
                List.of(new TurAIAgentChatAPI.ChatMessageItem("user", "Hi")), null, null);

        assertThatThrownBy(() -> api.chat("agent-1", request,
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no LLM instance configured");
    }

    // --- contextInfo Tests ---

    @Test
    void contextInfoShouldReturnProviderContextWindow() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted("enc");

        when(turAIAgentRepositoryPort.findById("agent-1")).thenReturn(Optional.of(agentDomain("agent-1")));
        when(turLLMInstanceRepositoryPort.findById("llm-1"))
                .thenReturn(Optional.of(llmDomain("llm-1", null)));
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
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted("enc");

        when(turAIAgentRepositoryPort.findById("agent-1")).thenReturn(Optional.of(agentDomain("agent-1")));
        when(turLLMInstanceRepositoryPort.findById("llm-1"))
                .thenReturn(Optional.of(llmDomain("llm-1", 64000)));
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
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted("enc");

        when(turAIAgentRepositoryPort.findById("agent-1")).thenReturn(Optional.of(agentDomain("agent-1")));
        when(turLLMInstanceRepositoryPort.findById("llm-1"))
                .thenReturn(Optional.of(llmDomain("llm-1", null)));
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
        when(turAIAgentRepositoryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.contextInfo("missing", "llm-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI Agent not found");
    }

    @Test
    void contextInfoShouldThrowWhenLlmNotFound() {
        when(turAIAgentRepositoryPort.findById("agent-1")).thenReturn(Optional.of(agentDomain("agent-1")));
        when(turLLMInstanceRepositoryPort.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> api.contextInfo("agent-1", "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("LLM instance not found");
    }

    // parseNativeTools and resolveUsername were moved to TurAgentChatExecutor
    // (private statics) when the chat pipeline was extracted. Their behavior
    // is exercised through the executor; the previous reflection-based unit
    // tests no longer apply.

    // --- contextInfo edge cases ---

    @Test
    void contextInfoShouldWorkWithNullApiKeyEncrypted() {
        TurLLMInstance instance = new TurLLMInstance();
        instance.setId("llm-1");
        instance.setApiKeyEncrypted(null);

        when(turAIAgentRepositoryPort.findById("agent-1")).thenReturn(Optional.of(agentDomain("agent-1")));
        when(turLLMInstanceRepositoryPort.findById("llm-1"))
                .thenReturn(Optional.of(llmDomain("llm-1", 32000)));
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
        var r1 = new TurAIAgentChatAPI.AgentChatRequest("llm-1", List.of(item), null, null);
        var r2 = new TurAIAgentChatAPI.AgentChatRequest("llm-1", List.of(item), null, null);

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

    // --- streamWorkspace() tests (T113, agent-scoped) ---

    @Test
    void streamWorkspaceEmitsInitialSnapshotThenRelaysBus() {
        // Snapshot back-fill from the workspace, plus a live put from the bus.
        when(agentWorkspace.list("agent-1", "conv-1", null)).thenReturn(List.of(
                new com.viglet.turing.genai.workspace.WorkspaceEntry(
                        "reports/a.csv", 12L, "text/csv", "2026-06-03T00:00:00Z",
                        "/api/v2/workspace/file?agentId=agent-1&conversationId=conv-1&key=reports%2Fa.csv")));
        when(workspaceEventBus.subscribe("conv-1")).thenReturn(reactor.core.publisher.Flux.just(
                com.viglet.turing.genai.workspace.TurWorkspaceEvent.delete("conv-1", "old.txt")));

        List<com.viglet.turing.genai.workspace.TurWorkspaceEvent> received = new ArrayList<>();
        var sub = api.streamWorkspace("agent-1", "conv-1")
                .map(sse -> sse.data())
                .filter(Objects::nonNull)
                .subscribe(received::add);

        // Both the initial snapshot put and the bus delete arrive synchronously
        // (fromIterable + Flux.just); the 25s heartbeat hasn't fired.
        assertThat(received).hasSize(2);
        assertThat(received.get(0).event())
                .isEqualTo(com.viglet.turing.genai.workspace.TurWorkspaceEvent.PUT);
        assertThat(received.get(0).key()).isEqualTo("reports/a.csv");
        assertThat(received.get(0).contentType()).isEqualTo("text/csv");
        assertThat(received.get(0).size()).isEqualTo(12L);
        assertThat(received.get(1).event())
                .isEqualTo(com.viglet.turing.genai.workspace.TurWorkspaceEvent.DELETE);
        assertThat(received.get(1).key()).isEqualTo("old.txt");
        sub.dispose();
    }

    @Test
    void streamWorkspaceBlankConversationIsEmpty() {
        List<com.viglet.turing.genai.workspace.TurWorkspaceEvent> received = new ArrayList<>();
        var sub = api.streamWorkspace("agent-1", "  ")
                .map(sse -> sse.data())
                .filter(Objects::nonNull)
                .subscribe(received::add);

        assertThat(received).isEmpty();
        sub.dispose();
    }
}
