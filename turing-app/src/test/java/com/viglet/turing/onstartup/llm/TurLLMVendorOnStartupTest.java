package com.viglet.turing.onstartup.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void seedsEveryVendorWhenNonePresent() {
        // existsById is unstubbed → false for all, so every vendor is created.
        turLLMVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurLLMVendor> captor = ArgumentCaptor.forClass(TurLLMVendor.class);
        verify(turLLMVendorRepository, times(13)).save(captor.capture());
        List<TurLLMVendor> vendors = captor.getAllValues();
        assertEquals("OPENAI", vendors.get(0).getId());
        assertEquals("OLLAMA", vendors.get(1).getId());
        assertEquals("ANTHROPIC", vendors.get(2).getId());
        assertEquals("GEMINI", vendors.get(3).getId());
        assertEquals("GEMINI_OPENAI", vendors.get(4).getId());
        // Block AD foundation providers (T505-T510, T521).
        assertEquals("OPENAI_COMPAT", vendors.get(5).getId());
        assertEquals("BEDROCK", vendors.get(6).getId());
        assertEquals("VOYAGE", vendors.get(7).getId());
        assertEquals("COHERE", vendors.get(8).getId());
        assertEquals("MISTRAL", vendors.get(9).getId());
        assertEquals("VERTEX_AI", vendors.get(10).getId());
        // T754 / ADR 0004 — the two in-process ONNX embedding vendors.
        assertEquals("TRANSFORMERS_LOCAL", vendors.get(11).getId());
        assertEquals("HUGGINGFACE", vendors.get(12).getId());
    }

    @Test
    void skipsVendorsThatAlreadyExistAndSeedsTheRest() {
        // OPENAI already present (e.g. an existing install) → not re-created; the
        // rest (including the two new ONNX vendors) are still added.
        when(turLLMVendorRepository.existsById("OPENAI")).thenReturn(true);

        turLLMVendorOnStartup.createDefaultRows();

        verify(turLLMVendorRepository, never()).save(argThat(v -> "OPENAI".equals(v.getId())));
        verify(turLLMVendorRepository, times(12)).save(any(TurLLMVendor.class));
    }
}
