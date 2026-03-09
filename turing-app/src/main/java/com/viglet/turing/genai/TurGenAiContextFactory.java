package com.viglet.turing.genai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurGenAiContextFactory {

    private final TurGenAiLlmProviderFactory llmProviderFactory;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurSecretCryptoService turSecretCryptoService;

    public TurGenAiContextFactory(TurGenAiLlmProviderFactory llmProviderFactory,
            TurGenAiStoreProviderFactory storeProviderFactory,
            TurSecretCryptoService turSecretCryptoService) {
        this.llmProviderFactory = llmProviderFactory;
        this.storeProviderFactory = storeProviderFactory;
        this.turSecretCryptoService = turSecretCryptoService;
    }

    public TurGenAiContext build(TurSNSiteGenAi turSNSiteGenAi) {
        if (turSNSiteGenAi == null || !turSNSiteGenAi.isEnabled()) {
            return TurGenAiContext.disabled();
        }

        TurLLMInstance turLLMInstance = turSNSiteGenAi.getTurLLMInstance();
        TurStoreInstance turStoreInstance = turSNSiteGenAi.getTurStoreInstance();
        if (turLLMInstance == null || turStoreInstance == null) {
            log.warn("GenAI enabled without complete LLM/store settings. siteGenAiId={}", turSNSiteGenAi.getId());
            return TurGenAiContext.disabled();
        }

        TurGenAiLlmProvider llmProvider = llmProviderFactory.getProvider(turLLMInstance);
        String llmApiKey = turSecretCryptoService.decrypt(turLLMInstance.getApiKeyEncrypted());
        EmbeddingModel embeddingModel = llmProvider.createEmbeddingModel(turLLMInstance, llmApiKey);
        ChatModel chatModel = llmProvider.createChatModel(turLLMInstance, llmApiKey);

        TurGenAiStoreProvider storeProvider = storeProviderFactory.getProvider(turStoreInstance);
        String storeCredential = turSecretCryptoService.decrypt(turStoreInstance.getCredentialEncrypted());
        VectorStore vectorStore = storeProvider.createVectorStore(turStoreInstance, embeddingModel, storeCredential);

        return TurGenAiContext.builder()
                .vectorStore(vectorStore)
                .embeddingModel(embeddingModel)
                .chatModel(chatModel)
                .enabled(true)
                .systemPrompt(turSNSiteGenAi.getSystemPrompt())
                .build();
    }
}
