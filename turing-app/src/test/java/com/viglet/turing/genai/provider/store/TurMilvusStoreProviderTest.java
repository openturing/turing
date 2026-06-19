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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

/**
 * Tests for TurMilvusStoreProvider.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
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

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
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

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void testCreateVectorStore_noCredential_withTokenFromOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("token"))).thenReturn("options-token");
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_noCredential_noToken() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("token"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @ParameterizedTest(name = "baseUrl={0}, metricType={1}, indexType={2}")
    @CsvSource(nullValues = "null", value = {
            "null,                INVALID_METRIC, INVALID_INDEX", // invalid metric/index type
            "http://custom:19530, null,           null",         // custom base url
            "null,                '',             ''"             // empty metric/index type strings
    })
    void testCreateVectorStore_metricAndBaseUrlVariants(String baseUrl, String metricType, String indexType) {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(baseUrl);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(false);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(metricType);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(indexType);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, "token");
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_withCollectionNameOverride() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(false);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, "token", "override_coll");
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_withNullCollectionNameOverride() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("token"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, null, null);
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_collectionNameFromInstance() {
        instance.setCollectionName("instance_collection");

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(false);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, "token");
            assertNotNull(store);
        }
    }

    @Test
    void testGetSystemInfo_returnsDownOnException() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid:19530");

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class,
                (mock2, ctx) -> when(mock2.getVersion()).thenThrow(new RuntimeException("connection refused")))) {
            Map<String, String> info = provider.getSystemInfo(instance, null);
            assertEquals("Milvus", info.get("store"));
            assertEquals("DOWN", info.get("status"));
            assertNotNull(info.get("error"));
        }
    }

    @Test
    void testCreateVectorStore_withDatabaseNameInOverrideVariant() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn("custom_db");
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(false);
        when(optionsParser.intValue(any(), eq("embeddingDimension"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexParameters"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("metricType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (var mocked = Mockito.mockConstruction(io.milvus.client.MilvusServiceClient.class)) {
            VectorStore store = provider.createVectorStore(instance, embeddingModel, "token", "override");
            assertNotNull(store);
        }
    }
}
