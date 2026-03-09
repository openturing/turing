package com.viglet.turing.genai.provider.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;

class TurGenAiStoreProviderFactoryTest {

    private TurGenAiStoreProviderFactory factory;
    private TurGenAiStoreProvider mockProvider;

    @BeforeEach
    void setUp() {
        mockProvider = mock(TurGenAiStoreProvider.class);
        when(mockProvider.getPluginType()).thenReturn("MockStore");
        factory = new TurGenAiStoreProviderFactory(List.of(mockProvider));
    }

    @Test
    void testGetProvider_successWithPlugin() {
        TurStoreInstance instance = new TurStoreInstance();
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setPlugin("mockstore");
        instance.setTurStoreVendor(vendor);

        TurGenAiStoreProvider result = factory.getProvider(instance);
        assertEquals(mockProvider, result);
    }

    @Test
    void testGetProvider_successWithId() {
        TurStoreInstance instance = new TurStoreInstance();
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setId("MOCKSTORE");
        instance.setTurStoreVendor(vendor);

        TurGenAiStoreProvider result = factory.getProvider(instance);
        assertEquals(mockProvider, result);
    }

    @Test
    void testGetProvider_missingVendor() {
        TurStoreInstance instance = new TurStoreInstance();
        assertThrows(IllegalStateException.class, () -> factory.getProvider(instance));
        assertThrows(IllegalStateException.class, () -> factory.getProvider(null));
    }

    @Test
    void testGetProvider_unsupportedPlugin() {
        TurStoreInstance instance = new TurStoreInstance();
        TurStoreVendor vendor = new TurStoreVendor();
        vendor.setPlugin("invalid");
        instance.setTurStoreVendor(vendor);

        assertThrows(IllegalStateException.class, () -> factory.getProvider(instance));
    }
}
