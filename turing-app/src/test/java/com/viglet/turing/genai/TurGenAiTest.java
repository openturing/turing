package com.viglet.turing.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.viglet.turing.sn.TurSNSearchProcess;

class TurGenAiTest {

    @Test
    void shouldReturnDisabledMessageWhenContextIsDisabled() {
        TurSNSearchProcess turSNSearchProcess = mock(TurSNSearchProcess.class);
        TurGenAiContextFactory contextFactory = mock(TurGenAiContextFactory.class);
        TurGenAi turGenAi = new TurGenAi(turSNSearchProcess, contextFactory);
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(false);

        TurChatMessage result = turGenAi.assistant(context, "any question");

        assertFalse(result.isEnabled());
        assertEquals("AI configuration is not enabled", result.getText());
        verifyNoInteractions(turSNSearchProcess);
    }

    @Test
    void shouldReturnEnabledMessageWithNullTextForWildcardSingleToken() {
        TurSNSearchProcess turSNSearchProcess = mock(TurSNSearchProcess.class);
        TurGenAiContextFactory contextFactory = mock(TurGenAiContextFactory.class);
        TurGenAi turGenAi = new TurGenAi(turSNSearchProcess, contextFactory);
        TurGenAiContext context = mock(TurGenAiContext.class);
        when(context.isEnabled()).thenReturn(true);

        TurChatMessage result = turGenAi.assistant(context, "*");

        assertTrue(result.isEnabled());
        assertNull(result.getText());
        verifyNoInteractions(turSNSearchProcess);
    }
}
