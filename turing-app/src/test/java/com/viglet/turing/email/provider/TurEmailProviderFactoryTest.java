package com.viglet.turing.email.provider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for TurEmailProviderFactory.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
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
    void getProviderShouldReturnMatchingProviderCaseInsensitive() {
        TurEmailProvider provider = mock(TurEmailProvider.class);
        when(provider.getProviderType()).thenReturn("SendGrid");

        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of(provider));

        assertSame(provider, factory.getProvider("sendgrid"));
        assertSame(provider, factory.getProvider("SENDGRID"));
        assertSame(provider, factory.getProvider("SendGrid"));
    }

    @Test
    void getProviderShouldThrowForUnknownType() {
        TurEmailProvider brevo = mock(TurEmailProvider.class);
        when(brevo.getProviderType()).thenReturn("BREVO");
        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of(brevo));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.getProvider("UNKNOWN"));
        assertTrue(ex.getMessage().contains("UNKNOWN"));
    }

    @ParameterizedTest(name = "getProvider throws for blank type [{0}]")
    @NullSource
    @EmptySource
    @ValueSource(strings = "  ")
    void getProviderShouldThrowForBlankType(String type) {
        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.getProvider(type));
    }

    @Test
    void factoryWithMultipleProvidersShouldResolveCorrectOne() {
        TurEmailProvider brevo = mock(TurEmailProvider.class);
        when(brevo.getProviderType()).thenReturn("BREVO");
        TurEmailProvider sendGrid = mock(TurEmailProvider.class);
        when(sendGrid.getProviderType()).thenReturn("SENDGRID");

        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of(brevo, sendGrid));

        assertSame(brevo, factory.getProvider("BREVO"));
        assertSame(sendGrid, factory.getProvider("SENDGRID"));
    }

    @Test
    void factoryWithEmptyProviderListShouldThrowForValidType() {
        TurEmailProviderFactory factory = new TurEmailProviderFactory(List.of());
        assertThrows(IllegalStateException.class, () -> factory.getProvider("BREVO"));
    }
}
