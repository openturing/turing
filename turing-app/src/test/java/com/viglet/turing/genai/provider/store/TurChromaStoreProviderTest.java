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
    void testCreateVectorStore_success() {
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
}
