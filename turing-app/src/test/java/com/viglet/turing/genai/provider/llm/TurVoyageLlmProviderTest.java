package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@ExtendWith(MockitoExtension.class)
class TurVoyageLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;

    @InjectMocks
    private TurVoyageLlmProvider provider;

    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        instance = new TurLLMInstance();
        instance.setId("test-id");
    }

    @Test
    void testGetPluginType() {
        assertEquals("voyage", provider.getPluginType());
    }

    @Test
    void testCreateChatModel_unsupported() {
        assertThrows(UnsupportedOperationException.class,
                () -> provider.createChatModel(instance, "key"));
    }

    @Test
    void testCreateEmbeddingModel_missingApiKey() {
        assertThrows(IllegalStateException.class,
                () -> provider.createEmbeddingModel(instance, null));
    }

    @Test
    void testCreateEmbeddingModel_supported() {
        instance.setModelName("voyage-3-large");
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.stringValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createEmbeddingModel(instance, "voyage-key"));
    }
}
