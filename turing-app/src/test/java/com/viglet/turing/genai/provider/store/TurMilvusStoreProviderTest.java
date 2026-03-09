package com.viglet.turing.genai.provider.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

@ExtendWith(MockitoExtension.class)
class TurMilvusStoreProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurMilvusStoreProvider provider;

    private TurStoreInstance instance;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        instance = new TurStoreInstance();
        instance.setId("test-store");
        instance.setUrl("http://localhost:19530");
        embeddingModel = mock(EmbeddingModel.class);
    }

    @Test
    void testGetPluginType() {
        assertEquals("milvus", provider.getPluginType());
    }

    @Test
    void testCreateVectorStore_success() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(false);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (org.mockito.MockedConstruction<io.milvus.client.MilvusServiceClient> mocked = org.mockito.Mockito
                .mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, "my-token");
            assertNotNull(store);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void testCreateVectorStore_withOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn("mydb");
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn("mycollection");
        when(optionsParser.stringValue(any(), eq("token"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(false);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(128);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn("{}");
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn("L2");
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn("IVF_FLAT");

        try (org.mockito.MockedConstruction<io.milvus.client.MilvusServiceClient> mocked = org.mockito.Mockito
                .mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
            assertEquals(1, mocked.constructed().size());
        }
    }
}
