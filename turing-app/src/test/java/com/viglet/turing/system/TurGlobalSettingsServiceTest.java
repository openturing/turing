package com.viglet.turing.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@ExtendWith(MockitoExtension.class)
class TurGlobalSettingsServiceTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;

    @Test
    void shouldReadConfiguredSeparatorCaseInsensitive() {
        TurConfigVar configVar = new TurConfigVar();
        configVar.setValue(" comma ");
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(configVar));

        TurGlobalSettingsService service = new TurGlobalSettingsService(turConfigVarRepository);
        TurGlobalDecimalSeparator result = service.getDecimalSeparator();

        assertThat(result).isEqualTo(TurGlobalDecimalSeparator.COMMA);
    }

    @Test
    void shouldFallbackToDefaultWhenConfigMissing() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.empty());

        TurGlobalSettingsService service = new TurGlobalSettingsService(turConfigVarRepository);

        assertThat(service.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
    }

    @Test
    void shouldFallbackToDefaultWhenConfigIsInvalidOrNull() {
        TurConfigVar invalid = new TurConfigVar();
        invalid.setValue("invalid");
        TurConfigVar nullValue = new TurConfigVar();
        nullValue.setValue(null);

        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(invalid), Optional.of(nullValue));

        TurGlobalSettingsService service = new TurGlobalSettingsService(turConfigVarRepository);

        assertThat(service.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
        assertThat(service.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
    }

    @Test
    void shouldUpdateExistingConfigVar() {
        TurConfigVar existing = new TurConfigVar();
        existing.setId(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        existing.setPath("/legacy");
        existing.setValue("DOT");

        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.of(existing));
        when(turConfigVarRepository.save(any(TurConfigVar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TurGlobalSettingsService service = new TurGlobalSettingsService(turConfigVarRepository);
        TurGlobalDecimalSeparator result = service.updateDecimalSeparator(TurGlobalDecimalSeparator.COMMA);

        assertThat(result).isEqualTo(TurGlobalDecimalSeparator.COMMA);
        assertThat(existing.getId()).isEqualTo(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        assertThat(existing.getPath()).isEqualTo(TurConfigVarOnStartup.GLOBAL_PATH);
        assertThat(existing.getValue()).isEqualTo("COMMA");
        verify(turConfigVarRepository).save(existing);
    }

    @Test
    void shouldCreateConfigVarWhenMissing() {
        when(turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR))
                .thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any(TurConfigVar.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TurGlobalSettingsService service = new TurGlobalSettingsService(turConfigVarRepository);
        service.updateDecimalSeparator(TurGlobalDecimalSeparator.DOT);

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository).save(captor.capture());
        TurConfigVar saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        assertThat(saved.getPath()).isEqualTo(TurConfigVarOnStartup.GLOBAL_PATH);
        assertThat(saved.getValue()).isEqualTo("DOT");
    }
}
