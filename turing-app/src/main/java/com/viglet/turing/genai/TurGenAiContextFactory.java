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
    private final TurDefaultAgentResolver turDefaultAgentResolver;

    public TurGenAiContextFactory(TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurRagContextBuilder turRagContextBuilder,
            TurDefaultAgentResolver turDefaultAgentResolver) {
        this.llmModelFactory = llmModelFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.turRagContextBuilder = turRagContextBuilder;
        this.turDefaultAgentResolver = turDefaultAgentResolver;
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi) {
        return build(turSNSiteGenAi, null);
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi, String collectionNameOverride) {
        // T622 — fall back to the global Default AI Agent when the site declares
        // none (fail-open, gated on a default being set). Note turSNSiteGenAi may
        // be null here (a site with no GenAI binding at all): the resolver still
        // returns the default agent, so the demo's search-only seed can chat.
        TurAIAgent agent = turDefaultAgentResolver.resolveEffectiveAgent(turSNSiteGenAi);
        if (agent == null || agent.getEnabled() != 1 || !agent.isRagEnabled()) {
            return TurGenAiContext.disabled();
        }

        // T790 / §LIV.1 (Block BF) — a site explicitly set to VECTORLESS_STRUCTURED
        // opts out of embeddings entirely. Return a disabled context so NEITHER the
        // batch reindex (reindexVectorStore → reindexLocale) NOR the per-document
        // real-time index path (TurSNGenAi.addDocument) ever embeds — regardless of
        // whether the effective agent (the site's own, or the global Default AI Agent
        // T622 fallback) has RAG enabled. This makes the per-site opt-out explicit and
        // fully decoupled from the default-agent fallback. VECTOR and HYBRID still
        // embed (needsVectorSetup() == true).
        if (turSNSiteGenAi != null && turSNSiteGenAi.getKnowledgeBaseMode() != null
                && !turSNSiteGenAi.getKnowledgeBaseMode().needsVectorSetup()) {
            log.debug("Vector context skipped: SN site GenAI knowledge-base mode is VECTORLESS_STRUCTURED "
                    + "(no embeddings), even though the effective agent has RAG enabled.");
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
                .groundingMode(agent.getGroundingMode())
                .ragInfrastructure(infra)
                // T500 — thread the per-site full-context opt-in + budget through
                // so TurSNGenAi.retrieveDocuments can bypass top-K when the corpus
                // fits. Default off → unchanged top-K path. When the site has no
                // GenAI binding (T622 default-agent fallback) there are no per-site
                // overrides, so full-context stays off.
                .fullContextEnabled(turSNSiteGenAi != null && turSNSiteGenAi.isFullContextAnsweringEnabled())
                .fullContextTokenBudget(turSNSiteGenAi == null ? 0 : turSNSiteGenAi.getFullContextTokenBudget())
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
