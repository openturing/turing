package com.viglet.turing.genai.provider.store;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Tests for TurPgVectorStoreProvider.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurPgVectorStoreProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    private TurPgVectorStoreProvider provider;

    private TurStoreInstance instance;
    private EmbeddingModel embeddingModel;

    @BeforeEach
    void setUp() {
        provider = new TurPgVectorStoreProvider(optionsParser);
        instance = new TurStoreInstance();
        instance.setId("test-pg-store");
        instance.setUrl("jdbc:postgresql://localhost:5432/testdb");
        embeddingModel = mock(EmbeddingModel.class);
    }

    @Test
    void testGetPluginType() {
        assertEquals("pgvector", provider.getPluginType());
    }

    @Test
    void testCreateVectorStore_defaultOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_withCredentialUserPass() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, "user:password123");
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_withCredentialPasswordOnly() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, "just-a-password");
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_withAllOptions() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn("jdbc:postgresql://other:5432/db");
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn("optUser");
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn("optPass");
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn("my_vectors");
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn("custom_schema");
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(Boolean.FALSE);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(1536);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn("COSINE_DISTANCE");
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn("HNSW");

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_withCollectionNameOverride() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null, "override_table");
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_invalidDistanceType() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn("INVALID_TYPE");
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn("ALSO_INVALID");

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_dimensionsZeroOrNegative() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(0);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @Test
    void testCreateVectorStore_usesCollectionNameFromInstance() {
        instance.setCollectionName("instance_collection");

        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }

    @Test
    void testListCollections_returnsEmptyOnException() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            List<TurStoreCollectionInfo> result = provider.listCollections(instance, null);
            // JdbcTemplate will fail because datasource is a mock, so it should return empty list
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testCreateCollection_throwsRuntimeExceptionOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            assertThrows(RuntimeException.class,
                    () -> provider.createCollection(instance, null, "test_collection"));
        }
    }

    @Test
    void testClearCollection_throwsRuntimeExceptionOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            assertThrows(RuntimeException.class,
                    () -> provider.clearCollection(instance, null, "test_collection"));
        }
    }

    @Test
    void testDeleteCollection_throwsRuntimeExceptionOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            assertThrows(RuntimeException.class,
                    () -> provider.deleteCollection(instance, null, "test_collection"));
        }
    }

    @Test
    void testDeleteByMetadata_doesNotThrowOnFailure() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            // Should not throw -- logs warning instead
            assertDoesNotThrow(() ->
                    provider.deleteByMetadata(instance, null, "test_collection", "source_id", "doc1"));
        }
    }

    @Test
    void testGetSystemInfo_returnsDownOnException() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            Map<String, String> info = provider.getSystemInfo(instance, null);
            assertEquals("PGVector", info.get("store"));
            assertEquals("DOWN", info.get("status"));
        }
    }

    @Test
    void testShutdown_closesDataSources() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> mocked = Mockito.mockConstruction(HikariDataSource.class)) {
            // Trigger dataSource creation
            provider.createVectorStore(instance, embeddingModel, null);
            assertEquals(1, mocked.constructed().size());

            // Shutdown should close datasources
            provider.shutdown();
            verify(mocked.constructed().get(0)).close();
        }
    }

    @Test
    void testShutdown_handlesCloseException() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(null);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> mocked = Mockito.mockConstruction(HikariDataSource.class,
                (mock2, ctx) -> doThrow(new RuntimeException("close failed")).when(mock2).close())) {
            provider.createVectorStore(instance, embeddingModel, null);

            // Should not throw even when close fails
            assertDoesNotThrow(() -> provider.shutdown());
        }
    }

    @Test
    void testCreateVectorStore_initializeSchemaFalse() {
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), eq("jdbcUrl"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("username"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("password"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("tableName"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("schemaName"))).thenReturn(null);
        when(optionsParser.booleanValue(any(), eq("initializeSchema"))).thenReturn(Boolean.FALSE);
        when(optionsParser.intValue(any(), eq("dimensions"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("distanceType"))).thenReturn(null);
        when(optionsParser.stringValue(any(), eq("indexType"))).thenReturn(null);

        try (MockedConstruction<HikariDataSource> ignored = Mockito.mockConstruction(HikariDataSource.class)) {
            var store = provider.createVectorStore(instance, embeddingModel, null);
            assertNotNull(store);
        }
    }
}
