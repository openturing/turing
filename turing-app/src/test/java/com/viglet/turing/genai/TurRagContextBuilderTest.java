package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
    private TurGenAiStoreProviderFactory storeProviderFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurEmbeddingModelRepository embeddingModelRepository;
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

    @Test
    void resolveEmbeddingModelShouldUseModelReferenceWhenPresent() {
        embModel.setModelReference("custom-embedding-ref");

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        EmbeddingModel result = builder.resolveEmbeddingModel(embModel);
        assertThat(result).isSameAs(springEmb);
    }

    @Test
    void resolveEmbeddingModelShouldFallbackToLlmModelNameWhenNoReference() {
        embModel.setModelReference(null);

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        EmbeddingModel result = builder.resolveEmbeddingModel(embModel);
        assertThat(result).isSameAs(springEmb);
    }

    @Test
    void resolveEmbeddingModelShouldFallbackToLlmModelNameWhenReferenceIsEmpty() {
        embModel.setModelReference("");

        EmbeddingModel springEmb = mock(EmbeddingModel.class);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("plain-key");
        when(llmModelFactory.createEmbeddingModel(any(TurLLMInstance.class), eq("plain-key"))).thenReturn(springEmb);

        EmbeddingModel result = builder.resolveEmbeddingModel(embModel);
        assertThat(result).isSameAs(springEmb);
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
    void buildTwoArgShouldDelegateToThreeArg() {
        when(embeddingModelRepository.findById("emb-1")).thenReturn(Optional.empty());
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("emb-1", "store-1");
        assertThat(result).isEmpty();
    }

    @Test
    void buildShouldReturnEmptyWhenBothIdsAreBlank() {
        Optional<TurRagContextBuilder.RagInfrastructure> result = builder.build("", "");
        assertThat(result).isEmpty();
    }

    // Helper interface for testing VectorStore that also implements InitializingBean
    interface InitializingVectorStore extends VectorStore, InitializingBean {
    }
}
