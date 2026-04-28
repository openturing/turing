package com.viglet.turing.onstartup.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurLLMVendorOnStartupTest {

    @Mock
    private TurLLMVendorRepository turLLMVendorRepository;

    @InjectMocks
    private TurLLMVendorOnStartup turLLMVendorOnStartup;

    @Test
    void shouldCreateDefaultRowsWhenRepositoryIsEmpty() {
        when(turLLMVendorRepository.findAll()).thenReturn(List.of());

        turLLMVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurLLMVendor> captor = ArgumentCaptor.forClass(TurLLMVendor.class);
        verify(turLLMVendorRepository, org.mockito.Mockito.times(5)).save(captor.capture());
        List<TurLLMVendor> vendors = captor.getAllValues();
        assertEquals("OPENAI", vendors.get(0).getId());
        assertEquals("OLLAMA", vendors.get(1).getId());
        assertEquals("ANTHROPIC", vendors.get(2).getId());
        assertEquals("GEMINI", vendors.get(3).getId());
        assertEquals("GEMINI_OPENAI", vendors.get(4).getId());

    }

    @Test
    void shouldNotCreateRowsWhenRepositoryHasData() {
        when(turLLMVendorRepository.findAll()).thenReturn(List.of(new TurLLMVendor()));

        turLLMVendorOnStartup.createDefaultRows();

        verify(turLLMVendorRepository, never()).save(org.mockito.ArgumentMatchers.any(TurLLMVendor.class));
    }
}
