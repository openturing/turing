package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.system.security.TurSecretCryptoService;

@ExtendWith(MockitoExtension.class)
class TurGenAiContextFactoryTest {

    @Mock
    private TurGenAiLlmProviderFactory llmProviderFactory;

    @Mock
    private TurGenAiStoreProviderFactory storeProviderFactory;

    @Mock
    private TurSecretCryptoService turSecretCryptoService;

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
        storeInstance.setCredentialEncrypted("store-encrypted");
        turSNSiteGenAi.setTurStoreInstance(storeInstance);

        turSNSiteGenAi.setSystemPrompt("test prompt");

        TurGenAiLlmProvider llmProvider = mock(TurGenAiLlmProvider.class);
        when(llmProviderFactory.getProvider(llmInstance)).thenReturn(llmProvider);
        when(turSecretCryptoService.decrypt("llm-encrypted")).thenReturn("llm-decrypted");

        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        ChatModel chatModel = mock(ChatModel.class);
        when(llmProvider.createEmbeddingModel(llmInstance, "llm-decrypted")).thenReturn(embeddingModel);
        when(llmProvider.createChatModel(llmInstance, "llm-decrypted")).thenReturn(chatModel);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(turSecretCryptoService.decrypt("store-encrypted")).thenReturn("store-decrypted");

        VectorStore vectorStore = mock(VectorStore.class);
        when(storeProvider.createVectorStore(storeInstance, embeddingModel, "store-decrypted")).thenReturn(vectorStore);

        TurGenAiContext context = factory.build(turSNSiteGenAi);

        assertTrue(context.isEnabled());
        assertNotNull(context.getVectorStore());
        assertNotNull(context.getEmbeddingModel());
        assertNotNull(context.getChatModel());
        assertEquals("test prompt", context.getSystemPrompt());
    }
}
