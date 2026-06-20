package com.viglet.turing.genai.provider.store.lucene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.provider.store.TurStoreChunkPage;
import com.viglet.turing.genai.provider.store.TurStoreCollectionInfo;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.tenant.TurTenantContext;

/**
 * Unit coverage for {@link TurLuceneStoreProvider} (the embedded Lucene KNN
 * vector store): collection lifecycle, vector-store creation + indexing, chunk
 * listing, metadata delete, system info — all against an embedded index in a
 * temp dir with a mocked {@link EmbeddingModel}. No external services.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurLuceneStoreProviderTest {

    @TempDir
    Path tempDir;

    private TurLuceneStoreProvider provider;
    private TurStoreInstance instance;

    @BeforeEach
    void setUp() {
        TurTenantContext tenantContext = mock(TurTenantContext.class);
        lenient().when(tenantContext.isTenancyEnabled()).thenReturn(false);
        provider = new TurLuceneStoreProvider(new TurProviderOptionsParser(), tenantContext);

        instance = new TurStoreInstance();
        instance.setId("store-1");
        instance.setTitle("Lucene Test");
        instance.setUrl(tempDir.toString());
        instance.setCollectionName("docs");
        instance.setProviderOptionsJson("{}");
    }

    @AfterEach
    void tearDown() {
        provider.shutdown();
    }

    private EmbeddingModel embeddingModel() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        lenient().when(model.embed(any(Document.class)))
                .thenAnswer(inv -> vectorFor(((Document) inv.getArgument(0)).getText()));
        lenient().when(model.embed(anyString()))
                .thenAnswer(inv -> vectorFor(inv.getArgument(0)));
        return model;
    }

    private static float[] vectorFor(String text) {
        float[] v = new float[8];
        int h = text == null ? 0 : text.hashCode();
        for (int i = 0; i < v.length; i++) {
            v[i] = ((h >> i) & 1) == 1 ? 1f : 0.1f;
        }
        return v;
    }

    private void indexSampleDocs(VectorStore store) {
        store.add(List.of(
                new Document("a", "alpha refund policy", Map.of("type", "faq")),
                new Document("b", "beta shipping details", Map.of("type", "guide"))));
    }

    @Test
    void pluginTypeIsLucene() {
        assertThat(provider.getPluginType()).isEqualTo("lucene");
    }

    @Test
    void createAndListCollections() {
        provider.createCollection(instance, null, "docs");
        List<TurStoreCollectionInfo> collections = provider.listCollections(instance, null);
        assertThat(collections).extracting(TurStoreCollectionInfo::name).contains("docs");
    }

    @Test
    void createVectorStoreIndexesAndListsChunks() {
        VectorStore store = provider.createVectorStore(instance, embeddingModel(), null);
        indexSampleDocs(store);

        TurStoreChunkPage page = provider.listChunks(instance, null, "docs", 0, 10, Map.of());
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.chunks()).hasSize(2);

        Map<String, String> info = provider.getSystemInfo(instance, null);
        assertThat(info).isNotEmpty();
    }

    @Test
    void deleteByMetadataRemovesMatchingChunks() {
        VectorStore store = provider.createVectorStore(instance, embeddingModel(), null);
        indexSampleDocs(store);

        provider.deleteByMetadata(instance, null, "docs", "type", "faq");

        TurStoreChunkPage page = provider.listChunks(instance, null, "docs", 0, 10, Map.of());
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.chunks()).first()
                .satisfies(c -> assertThat(c.content()).contains("beta"));
    }

    @Test
    void clearCollectionEmptiesIt() {
        VectorStore store = provider.createVectorStore(instance, embeddingModel(), null);
        indexSampleDocs(store);

        provider.clearCollection(instance, null, "docs");

        assertThat(provider.listChunks(instance, null, "docs", 0, 10, Map.of()).total()).isZero();
    }

    @Test
    void deleteCollectionRemovesItFromListing() {
        provider.createCollection(instance, null, "docs");
        provider.deleteCollection(instance, null, "docs");
        assertThat(provider.listCollections(instance, null))
                .extracting(TurStoreCollectionInfo::name).doesNotContain("docs");
    }
}
