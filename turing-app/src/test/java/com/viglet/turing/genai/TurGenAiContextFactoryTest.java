package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProvider;
import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

@ExtendWith(MockitoExtension.class)
class TurGenAiContextFactoryTest {

    @Mock
    private TurGenAiLlmProviderFactory llmProviderFactory;

    @Mock
    private TurSecretCryptoService turSecretCryptoService;

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;

    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;

    @Mock
    private TurRagContextBuilder turRagContextBuilder;

    @InjectMocks
    private TurGenAiContextFactory factory;

    private TurSNSiteGenAi turSNSiteGenAi;

    @BeforeEach
    void setUp() {
        turSNSiteGenAi = new TurSNSiteGenAi();
    }

    @Test
    void testBuild_disabled() {
        turSNSiteGenAi.setEnabled(false);
        TurGenAiContext context = factory.build(turSNSiteGenAi);
        assertFalse(context.isEnabled());
    }

    @Test
    void testBuild_enabledButMissingInstances() {
        turSNSiteGenAi.setEnabled(true);
        // No LLM instance set, global default is empty
        when(turGlobalSettingsService.getDefaultLlmId()).thenReturn("");
        TurGenAiContext context = factory.build(turSNSiteGenAi);
        assertFalse(context.isEnabled());
    }

    @Test
    void testBuild_success() {
        turSNSiteGenAi.setEnabled(true);

        TurLLMInstance llmInstance = new TurLLMInstance();
        llmInstance.setApiKeyEncrypted("llm-encrypted");
        turSNSiteGenAi.setTurLLMInstance(llmInstance);

        TurStoreInstance storeInstance = new TurStoreInstance();
        storeInstance.setId("store-1");
        turSNSiteGenAi.setTurStoreInstance(storeInstance);

        TurEmbeddingModel embeddingModel = new TurEmbeddingModel();
        embeddingModel.setId("emb-1");
        turSNSiteGenAi.setTurEmbeddingModelInstance(embeddingModel);

        turSNSiteGenAi.setSystemPrompt("test prompt");

        // Mock LLM provider
        TurGenAiLlmProvider llmProvider = mock(TurGenAiLlmProvider.class);
        when(llmProviderFactory.getProvider(llmInstance)).thenReturn(llmProvider);
        when(turSecretCryptoService.decrypt("llm-encrypted")).thenReturn("llm-decrypted");

        ChatModel chatModel = mock(ChatModel.class);
        when(llmProvider.createChatModel(llmInstance, "llm-decrypted")).thenReturn(chatModel);

        // Mock RAG context builder
        EmbeddingModel springEmbeddingModel = mock(EmbeddingModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        var ragInfra = new TurRagContextBuilder.RagInfrastructure(
                vectorStore, springEmbeddingModel, null, null, null, "turing");
        when(turRagContextBuilder.build("emb-1", "store-1", null))
                .thenReturn(Optional.of(ragInfra));

        TurGenAiContext context = factory.build(turSNSiteGenAi);

        assertTrue(context.isEnabled());
        assertNotNull(context.getVectorStore());
        assertNotNull(context.getEmbeddingModel());
        assertNotNull(context.getChatModel());
        assertEquals("test prompt", context.getSystemPrompt());
    }
}
