package com.viglet.turing.genai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds runtime GenAI contexts (vector store + embedding model + chat model)
 * from an SN site's GenAI binding. Since 2026.2.4 the binding holds only a
 * reference to a {@link TurAIAgent} — all RAG infrastructure choices come
 * from the agent.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
public class TurGenAiContextFactory {

    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurRagContextBuilder turRagContextBuilder;

    public TurGenAiContextFactory(TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurRagContextBuilder turRagContextBuilder) {
        this.llmModelFactory = llmModelFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.turRagContextBuilder = turRagContextBuilder;
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi) {
        return build(turSNSiteGenAi, null);
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi, String collectionNameOverride) {
        if (turSNSiteGenAi == null) {
            return TurGenAiContext.disabled();
        }
        TurAIAgent agent = turSNSiteGenAi.getTurAIAgent();
        if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) {
            return TurGenAiContext.disabled();
        }

        TurLLMInstance llmInstance = pickLlmInstance(agent);
        if (llmInstance == null) {
            log.warn("RAG enabled on agent without any LLM instance. agentId={}", agent.getId());
            return TurGenAiContext.disabled();
        }

        TurEmbeddingModel embeddingModel = agent.getTurEmbeddingModelInstance();
        TurStoreInstance storeInstance = agent.getTurStoreInstance();
        if (embeddingModel == null || storeInstance == null) {
            log.warn("RAG enabled on agent without embedding model or store. agentId={}", agent.getId());
            return TurGenAiContext.disabled();
        }

        log.debug("RAG resolved from agent: agentId={}, llmInstanceId={}, embeddingModelId={}, storeInstanceId={}, collection={}",
                agent.getId(), llmInstance.getId(), embeddingModel.getId(), storeInstance.getId(), collectionNameOverride);

        var ragInfra = turRagContextBuilder.build(embeddingModel.getId(), storeInstance.getId(), collectionNameOverride);
        if (ragInfra.isEmpty()) {
            log.warn("RAG enabled on agent but infrastructure could not be built. agentId={}", agent.getId());
            return TurGenAiContext.disabled();
        }

        String llmApiKey = turSecretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, llmApiKey);

        var infra = ragInfra.get();
        // System prompt always comes from the agent — sites no longer
        // override it.
        return TurGenAiContext.builder()
                .vectorStore(infra.vectorStore())
                .embeddingModel(infra.embeddingModel())
                .chatModel(chatModel)
                .enabled(true)
                .systemPrompt(agent.getSystemPrompt())
                .ragInfrastructure(infra)
                .build();
    }

    /**
     * Picks the first LLM instance attached to the agent. Agents currently
     * model a {@code Set<TurLLMInstance>}; for the SN GenAI binding we treat
     * the first one (by id ordering) as the active one.
     */
    private TurLLMInstance pickLlmInstance(TurAIAgent agent) {
        if (agent.getLlmInstances() == null || agent.getLlmInstances().isEmpty()) {
            return null;
        }
        return agent.getLlmInstances().stream()
                .filter(llm -> llm.getEnabled() == 1)
                .findFirst()
                .orElseGet(() -> agent.getLlmInstances().iterator().next());
    }
}
