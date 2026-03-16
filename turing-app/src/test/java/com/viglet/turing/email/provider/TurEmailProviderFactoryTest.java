package com.viglet.turing.email.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class TurEmailProviderFactoryTest {

    @Test
    void getProviderShouldReturnMatchingProvider() {
        TurEmailProvider brevo = mock(TurEmailProvider.class);
        when(brevo.getProviderType()).thenReturn("BREVO");

        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of(brevo));

        assertSame(brevo, factory.getProvider("BREVO"));
        assertSame(brevo, factory.getProvider("brevo"));
    }

    @Test
    void getProviderShouldThrowForUnknownType() {
        TurEmailProvider brevo = mock(TurEmailProvider.class);
        when(brevo.getProviderType()).thenReturn("BREVO");
        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of(brevo));

        assertThrows(IllegalStateException.class, () -> factory.getProvider("UNKNOWN"));
    }

    @Test
    void getProviderShouldThrowForNullType() {
        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getProvider(null));
    }

    @Test
    void getProviderShouldThrowForBlankType() {
        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getProvider("  "));
    }
}
