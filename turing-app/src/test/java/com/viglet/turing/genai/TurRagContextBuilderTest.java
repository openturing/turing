package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;

import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Tests for TurRagContextBuilder.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurRagContextBuilderTest {

    @Mock
    private TurLlmModelFactory llmModelFactory;
    @Mock
    private com.viglet.turing.genai.provider.llm.TurLocalEmbeddingModelFactory localEmbeddingModelFactory;
    @Mock
    private com.viglet.turing.genai.provider.llm.TurHuggingFaceEmbeddingModelFactory huggingFaceEmbeddingModelFactory;
    @Mock
    private TurGenAiStoreProviderFactory storeProviderFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurEmbeddingModelRepository embeddingModelRepository;
    @Mock
    private com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository instanceRepository;
    @Mock
    private TurStoreInstanceRepository storeInstanceRepository;

    @InjectMocks
    private TurRagContextBuilder builder;

    private TurLLMInstance llmInstance;
    private TurEmbeddingModel embModel;
    private TurStoreInstance storeInstance;

    @BeforeEach
    void setUp() {
        llmInstance = new TurLLMInstance();
        llmInstance.setId("llm-1");
        llmInstance.setUrl("http://llm.example.com");
        llmInstance.setApiKeyEncrypted("enc-key");
        llmInstance.setModelName("gpt-4");

        embModel = new TurEmbeddingModel();
        embModel.setId("emb-1");
        embModel.setModelName("text-embedding-3-small");
        embModel.setTurLLMInstance(llmInstance);

        storeInstance = new TurStoreInstance();
        storeInstance.setId("store-1");
        storeInstance.setCredentialEncrypted("store-enc");
        storeInstance.setCollectionName("my-collection");
    }

    @Test
    void resolveEmbeddingModelShouldThrowWhenNoLlmInstance() {
        TurEmbeddingModel noLlm = new TurEmbeddingModel();
        noLlm.setModelName("test");
        assertThatThrownBy(() -> builder.resolveEmbeddingModel(noLlm))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no LLM instance");
    }

    @ParameterizedTest(name = "modelReference=[{0}]")
    @NullSource
    @ValueSource(strings = {"custom-embedding-ref", ""})
    void resolveEmbeddingModelShouldResolveRegardlessOfModelReference(String modelReference) {
        embModel.setModelReference(modelReference);

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        EmbeddingModel result = builder.resolveEmbeddingModel(embModel);
        assertThat(result).isSameAs(springEmb);
    }

    @Test
    void resolveEmbeddingModelShouldDelegateToLocalFactoryForTransformersLocal() {
        TurEmbeddingModel local = new TurEmbeddingModel();
        local.setModelName("all-MiniLM-L6-v2");
        local.setProviderType("TRANSFORMERS_LOCAL");
        local.setModelPath("https://example.test/model.onnx");
        local.setTokenizerPath("https://example.test/tokenizer.json");

        EmbeddingModel onnx = mock(EmbeddingModel.class);
        when(localEmbeddingModelFactory.supports("TRANSFORMERS_LOCAL")).thenReturn(true);
        when(localEmbeddingModelFactory.resolve(local)).thenReturn(onnx);

        EmbeddingModel result = builder.resolveEmbeddingModel(local);

        // Local ONNX path is used — the cloud LLM factory is never consulted.
        assertThat(result).isSameAs(onnx);
        verifyNoInteractions(llmModelFactory);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void buildShouldReturnEmptyWhenEmbeddingModelIdIsBlank(String embId) {
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build(embId, "store-1");
        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void buildShouldReturnEmptyWhenStoreInstanceIdIsBlank(String storeId) {
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", storeId);
        assertThat(result).isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenEmbeddingModelNotFound() {
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.empty());
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", "store-1");
        assertThat(result).isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenStoreInstanceNotFound() {
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.empty());
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", "store-1");
        assertThat(result).isEmpty();
    }

    @Test
    void buildShouldReturnInfrastructureOnSuccess() {
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.of(storeInstance));

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(secretCryptoService.decrypt("store-enc")).thenReturn("store-plain");
        when(storeProvider.createVectorStore(storeInstance, springEmb, "store-plain")).thenReturn(vectorStore);

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", "store-1");
        assertThat(result).isPresent();
        assertThat(result.get().vectorStore()).isSameAs(vectorStore);
        assertThat(result.get().embeddingModel()).isSameAs(springEmb);
        assertThat(result.get().collectionName()).isEqualTo("my-collection");
    }

    @Test
    void buildShouldUseCollectionNameOverrideWhenProvided() {
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.of(storeInstance));

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(secretCryptoService.decrypt("store-enc")).thenReturn("store-plain");
        when(storeProvider.createVectorStore(storeInstance, springEmb, "store-plain", "override-coll"))
                .thenReturn(vectorStore);

        Optional<TurRagContextBuilder.RagInfrastructure> result =
                builder.build("emb-1", "store-1", "override-coll");
        assertThat(result).isPresent();
        assertThat(result.get().collectionName()).isEqualTo("override-coll");
    }

    @Test
    void buildShouldDefaultCollectionNameToTuringWhenNull() {
        storeInstance.setCollectionName(null);

        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.of(storeInstance));

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(secretCryptoService.decrypt("store-enc")).thenReturn("store-plain");
        when(storeProvider.createVectorStore(storeInstance, springEmb, "store-plain")).thenReturn(vectorStore);

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", "store-1");
        assertThat(result).isPresent();
        assertThat(result.get().collectionName()).isEqualTo("turing");
    }

    @Test
    void buildShouldReturnEmptyOnException() {
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.of(storeInstance));

        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("provider error"));

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", "store-1");
        assertThat(result).isEmpty();
    }

    @Test
    void buildFromGlobalSettingsShouldDelegateToGlobalIds() {
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("");

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.buildFromGlobalSettings();
        assertThat(result).isEmpty();
    }

    @Test
    void buildFromGlobalSettingsWithOverrideShouldDelegateToGlobalIds() {
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("");

        Optional<TurRagContextBuilder.RagInfrastructure> result =
                builder.buildFromGlobalSettings("custom-collection");
        assertThat(result).isEmpty();
    }

    @Test
    void buildFromGlobalSettingsShouldWorkWithValidIds() {
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.of(storeInstance));

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(secretCryptoService.decrypt("store-enc")).thenReturn("store-plain");
        when(storeProvider.createVectorStore(storeInstance, springEmb, "store-plain")).thenReturn(vectorStore);

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.buildFromGlobalSettings();
        assertThat(result).isPresent();
    }

    @Test
    void buildFromGlobalSettingsWithOverrideShouldWorkWithValidIds() {
        when(globalSettingsService.getDefaultEmbeddingModelId()).thenReturn("emb-1");
        when(globalSettingsService.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.of(embModel));
        when(storeInstanceRepository.findById("store-1")).thenReturn(Optional.of(storeInstance));

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(secretCryptoService.decrypt("store-enc")).thenReturn("store-plain");
        when(storeProvider.createVectorStore(storeInstance, springEmb, "store-plain", "override"))
                .thenReturn(vectorStore);

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.buildFromGlobalSettings("override");
        assertThat(result).isPresent();
        assertThat(result.get().collectionName()).isEqualTo("override");
    }

    @Test
    void ragInfrastructureRecordShouldHoldAllValues() {
        VectorStore vs = mock(VectorStore.class);
        EmbeddingModel em = mock(EmbeddingModel.class);
        TurGenAiStoreProvider sp = mock(TurGenAiStoreProvider.class);
        TurStoreInstance si = new TurStoreInstance();
        var infra = new TurRagContextBuilder.RagInfrastructure(vs, em, sp, si, "cred", "coll");
        assertThat(infra.vectorStore()).isSameAs(vs);
        assertThat(infra.embeddingModel()).isSameAs(em);
        assertThat(infra.storeProvider()).isSameAs(sp);
        assertThat(infra.storeInstance()).isSameAs(si);
        assertThat(infra.storeCredential()).isEqualTo("cred");
        assertThat(infra.collectionName()).isEqualTo("coll");
    }

    @Test
    void buildWithEntitiesShouldCallAfterPropertiesSetOnInitializingBeanVectorStore() throws Exception {
        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        when(storeProviderFactory.getProvider(storeInstance)).thenReturn(storeProvider);
        when(secretCryptoService.decrypt("store-enc")).thenReturn("store-plain");

        // Create a VectorStore that also implements InitializingBean
        InitializingVectorStore initVectorStore = mock(InitializingVectorStore.class);
        when(storeProvider.createVectorStore(storeInstance, springEmb, "store-plain")).thenReturn(initVectorStore);

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build(embModel, storeInstance, null);
        assertThat(result).isPresent();
        verify(initVectorStore).afterPropertiesSet();
    }

    @Test
    void buildWithEntitiesShouldReturnEmptyOnException() {
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("build error"));

        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build(embModel, storeInstance, null);
        assertThat(result).isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenBothIdsAreBlank() {
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("", "");
        assertThat(result).isEmpty();
    }

    @Test
    void reindexContextualShouldFallBackToVectorStoreAddForTextOnlyModel() {
        EmbeddingModel textOnly = mock(EmbeddingModel.class);
        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        var infra = new TurRagContextBuilder.RagInfrastructure(vectorStore, textOnly, storeProvider,
                storeInstance, "cred", "coll");
        var docs = java.util.List.of(
                org.springframework.ai.document.Document.builder().id("c1").text("chunk one").build(),
                org.springframework.ai.document.Document.builder().id("c2").text("chunk two").build());

        boolean contextual = builder.reindexByMetadataContextual(infra, docs, "objectName", "doc.pdf");

        assertThat(contextual).isFalse();
        verify(storeProvider).deleteByMetadata(storeInstance, "cred", "coll", "objectName", "doc.pdf");
        verify(vectorStore).add(docs);
    }

    @Test
    void reindexContextualShouldEmbedChunksTogetherAndUpsertPrecomputed() {
        EmbeddingModel contextualModel = mock(EmbeddingModel.class,
                org.mockito.Mockito.withSettings().extraInterfaces(
                        com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel.class));
        var asContextual = (com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel) contextualModel;
        when(asContextual.supportsContextualChunks()).thenReturn(true);
        when(asContextual.embedDocumentChunks(any()))
                .thenReturn(java.util.List.of(new float[] { 0.1f }, new float[] { 0.2f }));

        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        var infra = new TurRagContextBuilder.RagInfrastructure(vectorStore, contextualModel, storeProvider,
                storeInstance, "cred", "coll");
        var docs = java.util.List.of(
                org.springframework.ai.document.Document.builder().id("c1").text("chunk one").build(),
                org.springframework.ai.document.Document.builder().id("c2").text("chunk two").build());

        boolean contextual = builder.reindexByMetadataContextual(infra, docs, "objectName", "doc.pdf");

        assertThat(contextual).isTrue();
        verify(storeProvider).deleteByMetadata(storeInstance, "cred", "coll", "objectName", "doc.pdf");
        verify(storeProvider).importChunks(eq(storeInstance), eq("cred"), eq("coll"), any(), eq(contextualModel));
        // The per-chunk vector-store add() must NOT run on the contextual path.
        org.mockito.Mockito.verify(vectorStore, org.mockito.Mockito.never())
                .add(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void reindexContextualShouldFallBackWhenVectorCountMismatches() {
        EmbeddingModel contextualModel = mock(EmbeddingModel.class,
                org.mockito.Mockito.withSettings().extraInterfaces(
                        com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel.class));
        var asContextual = (com.viglet.turing.genai.provider.llm.TurContextualEmbeddingModel) contextualModel;
        when(asContextual.supportsContextualChunks()).thenReturn(true);
        // Only one vector for two chunks → mismatch → fall back.
        when(asContextual.embedDocumentChunks(any()))
                .thenReturn(java.util.List.of(new float[] { 0.1f }));

        VectorStore vectorStore = mock(VectorStore.class);
        TurGenAiStoreProvider storeProvider = mock(TurGenAiStoreProvider.class);
        var infra = new TurRagContextBuilder.RagInfrastructure(vectorStore, contextualModel, storeProvider,
                storeInstance, "cred", "coll");
        var docs = java.util.List.of(
                org.springframework.ai.document.Document.builder().id("c1").text("chunk one").build(),
                org.springframework.ai.document.Document.builder().id("c2").text("chunk two").build());

        boolean contextual = builder.reindexByMetadataContextual(infra, docs, "objectName", "doc.pdf");

        assertThat(contextual).isFalse();
        verify(vectorStore).add(docs);
    }

    // Helper interface for testing VectorStore that also implements InitializingBean
    interface InitializingVectorStore extends VectorStore, InitializingBean {
    }
}
