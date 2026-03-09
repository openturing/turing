package com.viglet.turing.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.system.TurGlobalDecimalSeparator;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
class TurGlobalSettingsAPITest {

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;

    @InjectMocks
    private TurGlobalSettingsAPI turGlobalSettingsAPI;

    @Test
    void shouldReturnCurrentGlobalSettings() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.COMMA);

        TurGlobalSettingsBean result = turGlobalSettingsAPI.getGlobalSettings();

        assertThat(result.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.COMMA);
        verify(turGlobalSettingsService).getDecimalSeparator();
    }

    @Test
    void shouldUpdateGlobalSettings() {
        TurGlobalSettingsBean payload = TurGlobalSettingsBean.builder()
                .decimalSeparator(TurGlobalDecimalSeparator.DOT)
                .build();
        when(turGlobalSettingsService.updateDecimalSeparator(TurGlobalDecimalSeparator.DOT))
                .thenReturn(TurGlobalDecimalSeparator.DOT);

        TurGlobalSettingsBean result = turGlobalSettingsAPI.updateGlobalSettings(payload);

        assertThat(result.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
        verify(turGlobalSettingsService).updateDecimalSeparator(TurGlobalDecimalSeparator.DOT);
    }
}
