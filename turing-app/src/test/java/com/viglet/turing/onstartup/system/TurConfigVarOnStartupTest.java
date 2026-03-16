package com.viglet.turing.onstartup.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@ExtendWith(MockitoExtension.class)
class TurConfigVarOnStartupTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;

    @InjectMocks
    private TurConfigVarOnStartup turConfigVarOnStartup;

    @Test
    void shouldCreateFirstTimeConfigVarWhenMissing() {
        when(turConfigVarRepository.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        turConfigVarOnStartup.createDefaultRows();

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository, org.mockito.Mockito.times(12)).save(captor.capture());

        List<TurConfigVar> savedVars = captor.getAllValues();
        assertTrue(savedVars.stream().anyMatch(configVar -> "FIRST_TIME".equals(configVar.getId())
                && "/system".equals(configVar.getPath())
                && "true".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DECIMAL_SEPARATOR".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "DOT".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_PYTHON_EXECUTABLE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DEFAULT_LLM".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_LLM_CACHE_ENABLED".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_LLM_CACHE_TTL_MS".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "3600000".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_LLM_CACHE_REGENERATE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_EMAIL_PROVIDER".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "BREVO".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_EMAIL_API_KEY".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_SENDER_EMAIL".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_SENDER_NAME".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_RECIPIENT_EMAIL".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
    }

    @Test
    void shouldNotCreateConfigVarWhenAlreadyExists() {
        when(turConfigVarRepository.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(new TurConfigVar()));

        turConfigVarOnStartup.createDefaultRows();

        verify(turConfigVarRepository, never()).save(org.mockito.ArgumentMatchers.any(TurConfigVar.class));
    }
}
