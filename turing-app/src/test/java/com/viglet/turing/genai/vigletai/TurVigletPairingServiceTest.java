package com.viglet.turing.genai.vigletai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;

import com.viglet.turing.api.exception.TurProviderException;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.properties.TurVigletAiProperty;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * T957 / §XIV.10.2 — device-code pairing. The one network hop is stubbed so the
 * flow's real branching (pending / slow down / expired / approved) and the
 * all-or-nothing completion are exercised without a live managed service.
 */
@ExtendWith(MockitoExtension.class)
class TurVigletPairingServiceTest {

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;
    @Mock
    private TurSecretCryptoService turSecretCryptoService;
    @Mock
    private TurLLMInstanceRepository turLLMInstanceRepository;
    @Mock
    private TurLLMVendorRepository turLLMVendorRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TurVigletInstallIdentityService installIdentityService;

    private TurVigletAiProperty property;
    private StubbedPairingService service;

    /** Captures the last POST and answers with whatever the test queued. */
    private static class StubbedPairingService extends TurVigletPairingService {

        private final List<String> paths = new ArrayList<>();
        private final List<Map<String, Object>> payloads = new ArrayList<>();
        private ResponseEntity<Map<String, Object>> next;

        StubbedPairingService(TurVigletAiProperty property, TurGlobalSettingsService settings,
                TurSecretCryptoService crypto, TurLLMInstanceRepository instances,
                TurLLMVendorRepository vendors, TurVigletInstallIdentityService identity,
                PlatformTransactionManager transactionManager) {
            super(property, settings, crypto, instances, vendors, identity, transactionManager);
        }

        void answerWith(HttpStatus status, Map<String, Object> body) {
            this.next = ResponseEntity.status(status).body(body);
        }

        @Override
        protected ResponseEntity<Map<String, Object>> post(String path, Map<String, Object> payload) {
            paths.add(path);
            payloads.add(payload);
            return next;
        }
    }

    @BeforeEach
    void setUp() {
        property = new TurVigletAiProperty();
        service = new StubbedPairingService(property, turGlobalSettingsService, turSecretCryptoService,
                turLLMInstanceRepository, turLLMVendorRepository, installIdentityService,
                transactionManager);
        lenient().when(turGlobalSettingsService.getOrCreateVigletAiInstallId()).thenReturn("install-uuid");
        lenient().when(installIdentityService.getOrCreatePublicKey()).thenReturn("pub-key-b64");
    }

    private TurLLMVendor vigletVendor() {
        TurLLMVendor vendor = new TurLLMVendor();
        vendor.setId("VIGLET");
        vendor.setPlugin("viglet");
        return vendor;
    }

    @Test
    void statusReportsUnpairedWhenNoManagedInstanceExists() {
        when(turLLMInstanceRepository.findByVendorId("VIGLET")).thenReturn(List.of());

        TurVigletPairing.Status status = service.status();

        assertFalse(status.paired());
        assertNull(status.llmInstanceId());
        assertFalse(status.defaultLlm());
        assertEquals("install-uuid", status.installId());
    }

    @Test
    void statusReportsPairedAndWhetherItIsTheDefault() {
        TurLLMInstance managed = new TurLLMInstance();
        managed.setId("managed-1");
        when(turLLMInstanceRepository.findByVendorId("VIGLET")).thenReturn(List.of(managed));
        when(turGlobalSettingsService.getDefaultLlmId()).thenReturn("managed-1");

        TurVigletPairing.Status status = service.status();

        assertTrue(status.paired());
        assertEquals("managed-1", status.llmInstanceId());
        assertTrue(status.defaultLlm());
    }

    @Test
    void startPostsTheInstallIdAndReturnsTheCodeToShow() {
        service.answerWith(HttpStatus.OK, Map.of(
                "device_code", "dev-123",
                "user_code", "ABCD-EFGH",
                "verification_uri", "https://viglet.cloud/activate",
                "interval", 7,
                "expires_in", 600));

        TurVigletPairing.Session session = service.start();

        assertEquals("/device/code", service.paths.get(0));
        assertEquals("install-uuid", service.payloads.get(0).get("install_id"));
        assertEquals("turing", service.payloads.get(0).get("product"));
        assertNotNull(service.payloads.get(0).get("install_version"));
        assertEquals("dev-123", session.deviceCode());
        assertEquals("ABCD-EFGH", session.userCode());
        assertEquals("https://viglet.cloud/activate", session.verificationUri());
        assertEquals(7, session.intervalSeconds());
        assertEquals(600, session.expiresInSeconds());
    }

    /**
     * T960 — the binding is registered by the attempt, not by a later call: the
     * credential the flow ends with has to be bound to a key the managed service
     * already knows, or there is a window where it is a plain bearer token.
     */
    @Test
    void startRegistersTheInstallPublicKey() {
        service.answerWith(HttpStatus.OK, Map.of(
                "device_code", "dev-123",
                "user_code", "ABCD-EFGH",
                "verification_uri", "https://viglet.cloud/activate"));

        service.start();

        assertEquals("pub-key-b64", service.payloads.get(0).get("install_public_key"));
        assertEquals("Ed25519", service.payloads.get(0).get("install_key_alg"));
    }

    @Test
    void startFallsBackToTheRfcDefaultsWhenTheServiceOmitsThem() {
        service.answerWith(HttpStatus.OK, Map.of(
                "device_code", "dev-123",
                "user_code", "ABCD-EFGH",
                "verification_uri", "https://viglet.cloud/activate"));

        TurVigletPairing.Session session = service.start();

        assertEquals(5, session.intervalSeconds());
        assertEquals(900, session.expiresInSeconds());
    }

    @Test
    void startRejectsAnAnswerMissingTheCodeToShow() {
        service.answerWith(HttpStatus.OK, Map.of("device_code", "dev-123"));

        assertThrows(TurProviderException.class, () -> service.start());
    }

    @Test
    void pollRequiresADeviceCode() {
        assertThrows(IllegalArgumentException.class, () -> service.poll("  "));
    }

    @Test
    void pollMapsTheRfcErrorVocabulary() {
        assertEquals(TurVigletPairingState.PENDING, pollWithError("authorization_pending"));
        assertEquals(TurVigletPairingState.SLOW_DOWN, pollWithError("slow_down"));
        assertEquals(TurVigletPairingState.EXPIRED, pollWithError("expired_token"));
        assertEquals(TurVigletPairingState.DENIED, pollWithError("access_denied"));
        // Anything unrecognised is treated as a denial rather than an endless wait.
        assertEquals(TurVigletPairingState.DENIED, pollWithError("something_new"));
    }

    private TurVigletPairingState pollWithError(String error) {
        service.answerWith(HttpStatus.BAD_REQUEST, Map.of("error", error));
        TurVigletPairing.Poll poll = service.poll("dev-123");
        assertNull(poll.llmInstanceId());
        return poll.state();
    }

    @Test
    void aPendingPollWritesNothing() {
        service.answerWith(HttpStatus.BAD_REQUEST, Map.of("error", "authorization_pending"));

        service.poll("dev-123");

        verify(turLLMInstanceRepository, never()).save(any(TurLLMInstance.class));
        verify(turGlobalSettingsService, never()).updateDefaultLlmId(any());
    }

    @Test
    void approvalStoresTheCredentialCreatesTheInstanceAndSetsTheDefault() {
        service.answerWith(HttpStatus.OK, Map.of("access_token", "vk-secret"));
        when(turLLMVendorRepository.findById("VIGLET")).thenReturn(Optional.of(vigletVendor()));
        when(turLLMInstanceRepository.findByVendorId("VIGLET")).thenReturn(List.of());
        when(turSecretCryptoService.encrypt("vk-secret")).thenReturn("encrypted");
        when(turGlobalSettingsService.getDefaultLlmId()).thenReturn("");
        when(turLLMInstanceRepository.save(any(TurLLMInstance.class))).thenAnswer(invocation -> {
            TurLLMInstance saved = invocation.getArgument(0);
            saved.setId("managed-new");
            return saved;
        });

        TurVigletPairing.Poll poll = service.poll("dev-123");

        assertEquals(TurVigletPairingState.APPROVED, poll.state());
        assertEquals("managed-new", poll.llmInstanceId());
        assertTrue(poll.defaultLlmSet());
        verify(turGlobalSettingsService).updateDefaultLlmId("managed-new");
    }

    @Test
    void anInstallThatAlreadyHasADefaultKeepsIt() {
        service.answerWith(HttpStatus.OK, Map.of("access_token", "vk-secret"));
        when(turLLMVendorRepository.findById("VIGLET")).thenReturn(Optional.of(vigletVendor()));
        when(turLLMInstanceRepository.findByVendorId("VIGLET")).thenReturn(List.of());
        when(turSecretCryptoService.encrypt("vk-secret")).thenReturn("encrypted");
        when(turGlobalSettingsService.getDefaultLlmId()).thenReturn("someone-elses-llm");
        when(turLLMInstanceRepository.save(any(TurLLMInstance.class))).thenAnswer(invocation -> {
            TurLLMInstance saved = invocation.getArgument(0);
            saved.setId("managed-new");
            return saved;
        });

        TurVigletPairing.Poll poll = service.poll("dev-123");

        assertEquals(TurVigletPairingState.APPROVED, poll.state());
        assertFalse(poll.defaultLlmSet());
        verify(turGlobalSettingsService, never()).updateDefaultLlmId(any());
    }

    @Test
    void rePairingRefreshesTheExistingInstanceInsteadOfStackingASecond() {
        TurLLMInstance existing = new TurLLMInstance();
        existing.setId("managed-1");
        existing.setApiKeyEncrypted("stale");
        service.answerWith(HttpStatus.OK, Map.of("access_token", "vk-fresh"));
        when(turLLMVendorRepository.findById("VIGLET")).thenReturn(Optional.of(vigletVendor()));
        when(turLLMInstanceRepository.findByVendorId("VIGLET")).thenReturn(List.of(existing));
        when(turSecretCryptoService.encrypt("vk-fresh")).thenReturn("encrypted-fresh");
        when(turGlobalSettingsService.getDefaultLlmId()).thenReturn("managed-1");
        when(turLLMInstanceRepository.save(any(TurLLMInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TurVigletPairing.Poll poll = service.poll("dev-123");

        assertEquals("managed-1", poll.llmInstanceId());
        assertEquals("encrypted-fresh", existing.getApiKeyEncrypted());
        assertEquals("viglet-fast", existing.getModelName());
    }

    @Test
    void approvalWithoutACredentialIsAFailureNotAPairing() {
        service.answerWith(HttpStatus.OK, Map.of());

        assertThrows(TurProviderException.class, () -> service.poll("dev-123"));
        verify(turLLMInstanceRepository, never()).save(any(TurLLMInstance.class));
    }

    @Test
    void completionRefusesWhenTheVendorRowIsMissing() {
        service.answerWith(HttpStatus.OK, Map.of("access_token", "vk-secret"));
        when(turLLMVendorRepository.findById("VIGLET")).thenReturn(Optional.empty());

        assertThrows(TurProviderException.class, () -> service.poll("dev-123"));
        verify(turLLMInstanceRepository, never()).save(any(TurLLMInstance.class));
    }
}
