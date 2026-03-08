package com.viglet.turing.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TurSecretCryptoServiceTest {

    @Test
    void shouldEncryptAndDecryptWithConfiguredKey() {
        MockEnvironment environment = new MockEnvironment();
        TurSecretCryptoService service = new TurSecretCryptoService(environment, "my-test-key");

        String encrypted = service.encrypt("secret-value");

        assertThat(encrypted)
            .isNotBlank()
            .isNotEqualTo("secret-value");
        assertThat(service.decrypt(encrypted)).isEqualTo("secret-value");
    }

    @Test
    void shouldEncryptAndDecryptWithFallbackKeyOutsideProduction() {
        MockEnvironment environment = new MockEnvironment();
        TurSecretCryptoService service = new TurSecretCryptoService(environment, "");

        String encrypted = service.encrypt("fallback-secret");

        assertThat(encrypted).isNotBlank();
        assertThat(service.decrypt(encrypted)).isEqualTo("fallback-secret");
    }

    @Test
    void shouldFailFastInProductionWithoutKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        TurSecretCryptoService service = new TurSecretCryptoService(environment, "");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.encrypt("secret"));

        assertThat(exception.getMessage()).contains("turing.ai.crypto.key must be configured in production");
    }

    @Test
    void shouldThrowWhenPayloadIsInvalid() {
        MockEnvironment environment = new MockEnvironment();
        TurSecretCryptoService service = new TurSecretCryptoService(environment, "my-test-key");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.decrypt("invalid-base64"));

        assertThat(exception.getMessage()).contains("Unable to decrypt provider secret");
    }
}
