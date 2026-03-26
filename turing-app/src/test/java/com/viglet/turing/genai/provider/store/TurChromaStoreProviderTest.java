package com.viglet.turing.genai.provider.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
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

/**
 * Tests for TurChromaStoreProvider.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurChromaStoreProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurChromaStoreProvider provider;

    private TurStoreInstance instance;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        instance = new TurStoreInstance();
        instance.setId("test-store");
        instance.setUrl("http://localhost:8000");
        embeddingModel = mock(EmbeddingModel.class);
    }

    @Test
    void testGetPluginType() {
        assertEquals("chroma", provider.getPluginType());
    }

    @Test
    void testCreateVectorStore_basicAuth() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "basic:user:pass");
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_tokenAuth() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "token:my-token");
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_rawTokenAuth() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "raw-token-value");
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_noCredential_withKeyToken() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn("options-key-token");
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_noCredential_withBasicAuthOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn("admin");
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn("secret");

        VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_noCredential_noAuthOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, null);
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_withCustomOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://custom:9000");
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn("my_collection");
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn("my_tenant");
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn("my_database");
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(Boolean.FALSE);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "token:t");
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_withCollectionNameOverride() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "token:t", "override_coll");
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_withNullCollectionNameOverride() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "token:t", null);
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_usesCollectionNameFromInstance() {
        instance.setCollectionName("instance_collection");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "token:t");
        assertNotNull(store);
    }

    @Test
    void testListCollections_returnsEmptyOnException() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid:9999");
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        List<TurStoreCollectionInfo> result = provider.listCollections(instance, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCreateCollection_throwsRuntimeExceptionOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid:9999");
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> provider.createCollection(instance, null, "test_collection"));
    }

    @Test
    void testClearCollection_throwsRuntimeExceptionOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid:9999");
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> provider.clearCollection(instance, null, "test_collection"));
    }

    @Test
    void testDeleteCollection_throwsRuntimeExceptionOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid:9999");
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> provider.deleteCollection(instance, null, "test_collection"));
    }

    @Test
    void testDeleteByMetadata_doesNotThrowOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid:9999");
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        // Should not throw -- logs warning instead
        provider.deleteByMetadata(instance, null, "test_collection", "source_id", "doc1");
    }

    @Test
    void testGetSystemInfo_returnsDownOnConnectionFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn("http://invalid-host:9999");

        Map<String, String> info = provider.getSystemInfo(instance, null);
        assertEquals("ChromaDB", info.get("store"));
        assertEquals("DOWN", info.get("status"));
        assertTrue(info.containsKey("error"));
    }

    @Test
    void testCreateVectorStore_basicAuthWithIncompleteCredentials() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);

        // "basic:" prefix but no colon separator for user:pass - only one part
        VectorStore store = provider.createVectorStore(instance, embeddingModel, "basic:onlyuser");
        assertNotNull(store);
    }

    @Test
    void testCreateVectorStore_emptyCredential() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("baseUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("collectionName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tenantName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("databaseName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("keyToken"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicUsername"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("basicPassword"))).thenReturn(null);

        VectorStore store = provider.createVectorStore(instance, embeddingModel, "");
        assertNotNull(store);
    }
}
