package com.viglet.turing.onstartup.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@ExtendWith(MockitoExtension.class)
class TurLocaleOnStartupTest {

    @Mock
    private TurLocaleRepository turLocaleRepository;

    @InjectMocks
    private TurLocaleOnStartup turLocaleOnStartup;

    @Test
    void shouldCreateDefaultLocalesWhenRepositoryIsEmpty() {
        when(turLocaleRepository.findAll()).thenReturn(List.of());

        turLocaleOnStartup.createDefaultRows();

        ArgumentCaptor<TurLocale> captor = ArgumentCaptor.forClass(TurLocale.class);
        verify(turLocaleRepository, atLeast(40)).save(captor.capture());

        List<TurLocale> savedLocales = captor.getAllValues();
        assertTrue(savedLocales.stream().anyMatch(locale -> Locale.ENGLISH.equals(locale.getInitials())));
        assertTrue(savedLocales.stream().anyMatch(locale -> Locale.forLanguageTag("pt-BR").equals(locale.getInitials())
                || "pt_BR".equals(String.valueOf(locale.getInitials()))));
    }

    @Test
    void shouldNotCreateDefaultLocalesWhenRepositoryHasData() {
        when(turLocaleRepository.findAll()).thenReturn(List.of(new TurLocale()));

        turLocaleOnStartup.createDefaultRows();

        verify(turLocaleRepository, never()).save(org.mockito.ArgumentMatchers.any(TurLocale.class));
    }
}
