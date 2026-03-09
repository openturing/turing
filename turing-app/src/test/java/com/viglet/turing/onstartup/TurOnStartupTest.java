package com.viglet.turing.onstartup;

import static org.mockito.Mockito.never;
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

    @InjectMocks
    private TurOnStartup turOnStartup;

    @Test
    void shouldExecuteStartupFlowAccordingToEnvironmentPasswordWhenFirstTime() {
        when(turConfigVarRepository.findById(TurOnStartup.FIRST_TIME)).thenReturn(Optional.empty());

        turOnStartup.run(null);

        String turAdminPassword = System.getenv(TurOnStartup.TURING_ADMIN_PASSWORD);
        boolean hasValidPassword = turAdminPassword != null
                && turAdminPassword.trim().length() >= TurOnStartup.PASSWORD_MINIMUM_SIZE;

        if (hasValidPassword) {
            verify(turLocaleOnStartup).createDefaultRows();
            verify(turRoleOnStartup).createDefaultRows();
            verify(turGroupOnStartup).createDefaultRows();
            verify(turUserOnStartup).createDefaultRows(turAdminPassword);
            verify(turSEVendorOnStartup).createDefaultRows();
            verify(turLLMVendorOnStartup).createDefaultRows();
            verify(turStoreVendorOnStartup).createDefaultRows();
            verify(turConfigVarOnStartup).createDefaultRows();
        } else {
            verify(turLocaleOnStartup, never()).createDefaultRows();
            verify(turRoleOnStartup, never()).createDefaultRows();
            verify(turGroupOnStartup, never()).createDefaultRows();
            verify(turUserOnStartup, never()).createDefaultRows(org.mockito.ArgumentMatchers.anyString());
            verify(turSEVendorOnStartup, never()).createDefaultRows();
            verify(turLLMVendorOnStartup, never()).createDefaultRows();
            verify(turStoreVendorOnStartup, never()).createDefaultRows();
            verify(turConfigVarOnStartup, never()).createDefaultRows();
        }
    }

    @Test
    void shouldDoNothingWhenFirstTimeFlagAlreadyExists() {
        when(turConfigVarRepository.findById(TurOnStartup.FIRST_TIME))
                .thenReturn(Optional.of(new com.viglet.turing.persistence.model.system.TurConfigVar()));

        turOnStartup.run(null);

        verifyNoInteractions(turLocaleOnStartup, turSEVendorOnStartup, turLLMVendorOnStartup, turStoreVendorOnStartup,
                turConfigVarOnStartup, turUserOnStartup, turGroupOnStartup, turRoleOnStartup);
    }
}
