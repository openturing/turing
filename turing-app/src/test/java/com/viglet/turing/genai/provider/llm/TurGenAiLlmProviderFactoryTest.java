package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;

class TurGenAiLlmProviderFactoryTest {

    private TurGenAiLlmProviderFactory factory;
    private TurGenAiLlmProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = mock(TurGenAiLlmProvider.class);
        when(mockProvider.getPluginType()).thenReturn("MockPlugin");
        factory = new TurGenAiLlmProviderFactory(List.of(mockProvider));
    }

    @Test
    void testGetProvider_successWithPlugin() {
        TurLLMInstance instance = new TurLLMInstance();
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setPlugin("mockplugin");
        instance.setTurLLMVendor(vendor);

        TurGenAiLlmProvider result = factory.getProvider(instance);
        assertEquals(mockProvider, result);
    }

    @Test
    void testGetProvider_successWithId() {
        TurLLMInstance instance = new TurLLMInstance();
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("MOCKPLUGIN");
        instance.setTurLLMVendor(vendor);

        TurGenAiLlmProvider result = factory.getProvider(instance);
        assertEquals(mockProvider, result);
    }

    @Test
    void testGetProvider_missingVendor() {
        TurLLMInstance instance = new TurLLMInstance();
        assertThrows(IllegalStateException.class, () -> factory.getProvider(instance));
        assertThrows(IllegalStateException.class, () -> factory.getProvider(null));
    }

    @Test
    void testGetProvider_unsupportedPlugin() {
        TurLLMInstance instance = new TurLLMInstance();
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setPlugin("invalid");
        instance.setTurLLMVendor(vendor);

        assertThrows(IllegalStateException.class, () -> factory.getProvider(instance));
    }
}
