package com.viglet.turing.genai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurGenAiContextFactory {

    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurRagContextBuilder turRagContextBuilder;

    public TurGenAiContextFactory(TurGenAiLlmProviderFactory llmProviderFactory,
            TurSecretCryptoService turSecretCryptoService,
            TurGlobalSettingsService turGlobalSettingsService,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurRagContextBuilder turRagContextBuilder) {
        this.llmProviderFactory = llmProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turRagContextBuilder = turRagContextBuilder;
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi) {
        return build(turSNSiteGenAi, null);
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi, String collectionNameOverride) {
        if (turSNSiteGenAi == null || !turSNSiteGenAi.isEnabled()) {
            return TurGenAiContext.disabled();
        }

        // Resolve LLM Instance (for chat)
        TurLLMInstance llmInstance = resolveLlmInstance(turSNSiteGenAi.getTurLLMInstance());
        if (llmInstance == null) {
            log.warn("GenAI enabled without LLM instance. siteGenAiId={}", turSNSiteGenAi.getId());
            return TurGenAiContext.disabled();
        }

        // Resolve Embedding Model and Store via shared TurRagContextBuilder
        String embeddingModelId = resolveEmbeddingModelId(turSNSiteGenAi.getTurEmbeddingModelInstance());
        String storeInstanceId = resolveStoreInstanceId(turSNSiteGenAi.getTurStoreInstance());
        log.info("RAG resolved settings for siteGenAiId={}: llmInstanceId={}, embeddingModelId={}, storeInstanceId={}, collection={}",
                turSNSiteGenAi.getId(), llmInstance.getId(), embeddingModelId, storeInstanceId, collectionNameOverride);

        var ragInfra = turRagContextBuilder.build(embeddingModelId, storeInstanceId, collectionNameOverride);
        if (ragInfra.isEmpty()) {
            log.warn("GenAI enabled without complete embedding/store settings. siteGenAiId={}, embeddingModelId={}, storeInstanceId={}",
                    turSNSiteGenAi.getId(), embeddingModelId, storeInstanceId);
            return TurGenAiContext.disabled();
        }

        // Build ChatModel from LLM Instance
        TurGenAiLlmProvider llmProvider = llmProviderFactory.getProvider(llmInstance);
        String llmApiKey = turSecretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        ChatModel chatModel = llmProvider.createChatModel(llmInstance, llmApiKey);

        var infra = ragInfra.get();
        return TurGenAiContext.builder()
                .vectorStore(infra.vectorStore())
                .embeddingModel(infra.embeddingModel())
                .chatModel(chatModel)
                .enabled(true)
                .systemPrompt(turSNSiteGenAi.getSystemPrompt())
                .ragInfrastructure(infra)
                .build();
    }

    private TurLLMInstance resolveLlmInstance(TurLLMInstance configured) {
        if (configured != null) {
            return configured;
        }
        String defaultId = turGlobalSettingsService.getDefaultLlmId();
        if (StringUtils.hasText(defaultId)) {
            return turLLMInstanceRepository.findById(defaultId).orElse(null);
        }
        return null;
    }

    private String resolveEmbeddingModelId(TurEmbeddingModel configured) {
        if (configured != null) {
            return configured.getId();
        }
        return turGlobalSettingsService.getDefaultEmbeddingModelId();
    }

    private String resolveStoreInstanceId(TurStoreInstance configured) {
        if (configured != null) {
            return configured.getId();
        }
        return turGlobalSettingsService.getDefaultEmbeddingStoreId();
    }
}
