package com.viglet.turing.onstartup;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.onstartup.auth.TurGroupOnStartup;
import com.viglet.turing.onstartup.auth.TurRoleOnStartup;
import com.viglet.turing.onstartup.auth.TurUserOnStartup;
import com.viglet.turing.onstartup.llm.TurEmbeddingModelUnificationService;
import com.viglet.turing.onstartup.llm.TurLLMVendorOnStartup;
import com.viglet.turing.onstartup.se.TurSEVendorOnStartup;
import com.viglet.turing.onstartup.store.TurStoreVendorOnStartup;
import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.onstartup.system.TurLocaleOnStartup;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@ExtendWith(MockitoExtension.class)
class TurOnStartupTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;
    @Mock
    private TurLocaleOnStartup turLocaleOnStartup;
    @Mock
    private TurSEVendorOnStartup turSEVendorOnStartup;
    @Mock
    private TurLLMVendorOnStartup turLLMVendorOnStartup;
    @Mock
    private TurStoreVendorOnStartup turStoreVendorOnStartup;
    @Mock
    private TurConfigVarOnStartup turConfigVarOnStartup;
    @Mock
    private TurUserOnStartup turUserOnStartup;
    @Mock
    private TurGroupOnStartup turGroupOnStartup;
    @Mock
    private TurRoleOnStartup turRoleOnStartup;
    @Mock
    private TurEmbeddingModelUnificationService turEmbeddingModelUnificationService;

    @InjectMocks
    private TurOnStartup turOnStartup;

    @Test
    void shouldExecuteStartupFlowWhenFirstTime() {
        when(turConfigVarRepository.findById(TurOnStartup.FIRST_TIME)).thenReturn(Optional.empty());

        turOnStartup.run(null);

        verify(turLocaleOnStartup).createDefaultRows();
        verify(turRoleOnStartup).createDefaultRows();
        verify(turGroupOnStartup).createDefaultRows();
        verify(turUserOnStartup).createDefaultRows();
        verify(turSEVendorOnStartup).createDefaultRows();
        verify(turStoreVendorOnStartup).createDefaultRows();
        verify(turConfigVarOnStartup).createDefaultRows();
        // LLM vendor seeding runs in the first-time block AND the always-run section.
        verify(turLLMVendorOnStartup, times(2)).createDefaultRows();
        verify(turEmbeddingModelUnificationService).reconcile();
    }

    @Test
    void shouldStillRunIdempotentReconciliationsWhenAlreadyConfigured() {
        when(turConfigVarRepository.findById(TurOnStartup.FIRST_TIME))
                .thenReturn(Optional.of(new com.viglet.turing.persistence.model.system.TurConfigVar()));

        turOnStartup.run(null);

        // The first-time-only seeders stay untouched...
        verifyNoInteractions(turLocaleOnStartup, turSEVendorOnStartup, turStoreVendorOnStartup,
                turConfigVarOnStartup, turUserOnStartup, turGroupOnStartup, turRoleOnStartup);
        // ...but the idempotent reconciliations run every boot so existing installs
        // pick up new vendors + the embedding-model unification.
        verify(turLLMVendorOnStartup).createDefaultRows();
        verify(turEmbeddingModelUnificationService).reconcile();
    }
}
