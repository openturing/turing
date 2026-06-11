package com.viglet.turing.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Tests for TurSecretCryptoService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSecretCryptoServiceTest {

    @Test
    void shouldEncryptAndDecryptWithConfiguredKey() {
        var service = createService("my-test-key");

        String encrypted = service.encrypt("secret-value");

        assertThat(encrypted).isNotBlank().isNotEqualTo("secret-value");
        assertThat(service.decrypt(encrypted)).isEqualTo("secret-value");
    }

    @Test
    void shouldEncryptAndDecryptWithFallbackKeyOutsideProduction() {
        var service = createService("");

        String encrypted = service.encrypt("fallback-secret");

        assertThat(encrypted).isNotBlank();
        assertThat(service.decrypt(encrypted)).isEqualTo("fallback-secret");
    }

    @Test
    void shouldFailFastInProductionWithoutKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        var service = new TurSecretCryptoService(environment, "");

        var exception = assertThrows(IllegalStateException.class,
                () -> service.encrypt("secret"));

        assertThat(exception.getMessage()).contains("turing.ai.crypto.key must be configured in production");
    }

    @Test
    void shouldFailFastInProductionWithNullKey() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        var service = new TurSecretCryptoService(environment, null);

        assertThatThrownBy(() -> service.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("turing.ai.crypto.key must be configured in production");
    }

    @Test
    void shouldFailInProductionCaseInsensitiveProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("PRODUCTION");
        var service = new TurSecretCryptoService(environment, "");

        assertThatThrownBy(() -> service.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAllowFallbackKeyWhenProfileIsNotProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("development", "test");
        var service = new TurSecretCryptoService(environment, "");

        String encrypted = service.encrypt("dev-secret");
        assertThat(service.decrypt(encrypted)).isEqualTo("dev-secret");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void encryptShouldReturnNullForNullOrBlankInput(String input) {
        var service = createService("key");
        assertThat(service.encrypt(input)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void decryptShouldReturnNullForNullOrBlankInput(String input) {
        var service = createService("key");
        assertThat(service.decrypt(input)).isNull();
    }

    @Test
    void shouldThrowWhenDecryptingInvalidBase64() {
        var service = createService("my-test-key");

        assertThatThrownBy(() -> service.decrypt("!!!not-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to decrypt provider secret");
    }

    @Test
    void shouldThrowWhenPayloadTooShort() {
        var service = createService("my-test-key");
        // Payload of exactly 12 bytes (IV_SIZE) — no ciphertext
        String shortPayload = Base64.getEncoder().encodeToString(new byte[12]);

        assertThatThrownBy(() -> service.decrypt(shortPayload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to decrypt provider secret");
    }

    @Test
    void shouldThrowWhenPayloadShorterThanIvSize() {
        var service = createService("my-test-key");
        String tinyPayload = Base64.getEncoder().encodeToString(new byte[5]);

        assertThatThrownBy(() -> service.decrypt(tinyPayload))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrowWhenDecryptingWithWrongKey() {
        var serviceA = createService("key-A");
        var serviceB = createService("key-B");

        String encrypted = serviceA.encrypt("sensitive-data");

        assertThatThrownBy(() -> serviceB.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to decrypt provider secret");
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        var service = createService("same-key");

        String encrypted1 = service.encrypt("same-value");
        String encrypted2 = service.encrypt("same-value");

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(service.decrypt(encrypted1)).isEqualTo("same-value");
        assertThat(service.decrypt(encrypted2)).isEqualTo("same-value");
    }

    @Test
    void shouldHandleUnicodeText() {
        var service = createService("unicode-key");

        String unicodeText = "Turing \u00e9 incr\u00edvel \ud83d\ude80 \u4f60\u597d";
        String encrypted = service.encrypt(unicodeText);

        assertThat(service.decrypt(encrypted)).isEqualTo(unicodeText);
    }

    @Test
    void shouldHandleLongPlaintext() {
        var service = createService("long-key");

        String longText = "A".repeat(10_000);
        String encrypted = service.encrypt(longText);

        assertThat(service.decrypt(encrypted)).isEqualTo(longText);
    }

    @Test
    void shouldHandleSingleCharacterPlaintext() {
        var service = createService("single-char-key");

        String encrypted = service.encrypt("x");

        assertThat(service.decrypt(encrypted)).isEqualTo("x");
    }

    @Test
    void shouldThrowWhenDecryptingTamperedCiphertext() {
        var service = createService("tamper-key");

        String encrypted = service.encrypt("original");
        byte[] payload = Base64.getDecoder().decode(encrypted);
        // Flip a byte in the ciphertext portion (after IV)
        payload[payload.length - 1] ^= 0xFF;
        String tampered = Base64.getEncoder().encodeToString(payload);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldWorkWithMultipleProfilesIncludingProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("monitoring", "production");
        var service = new TurSecretCryptoService(environment, "");

        assertThatThrownBy(() -> service.encrypt("secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("turing.ai.crypto.key must be configured in production");
    }

    @Test
    void shouldEncryptSpecialCharacters() {
        var service = createService("special-key");

        String specialText = "p@$$w0rd!#%^&*(){}[]|\\:\";<>?,./~`";
        String encrypted = service.encrypt(specialText);

        assertThat(service.decrypt(encrypted)).isEqualTo(specialText);
    }

    @Test
    void outputShouldBeValidBase64() {
        var service = createService("base64-key");

        String encrypted = service.encrypt("test");

        assertThat(encrypted).matches("^[A-Za-z0-9+/=]+$");
    }

    @Test
    void shouldDecryptSuccessfullyWithSameServiceInstance() {
        var service = createService("roundtrip-key");

        for (int i = 0; i < 10; i++) {
            String plaintext = "value-" + i;
            String encrypted = service.encrypt(plaintext);
            assertThat(service.decrypt(encrypted)).isEqualTo(plaintext);
        }
    }

    private TurSecretCryptoService createService(String key) {
        return new TurSecretCryptoService(new MockEnvironment(), key);
    }

    // ---- T271 / §XIV.4.5 per-tenant key derivation ------------------------------

    @org.junit.jupiter.api.Test
    void perTenantRoundTripWorksAndIsIsolatedAcrossTenants() {
        var service = createService("master-key");

        String forA = service.encrypt("sk-secret", "tenantA");
        String forB = service.encrypt("sk-secret", "tenantB");

        // Each tenant decrypts its own ciphertext.
        assertThat(service.decrypt(forA, "tenantA")).isEqualTo("sk-secret");
        assertThat(service.decrypt(forB, "tenantB")).isEqualTo("sk-secret");

        // Tenant B cannot decrypt tenant A's ciphertext (different derived key).
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.decrypt(forA, "tenantB"))
                .isInstanceOf(IllegalStateException.class);
    }

    @org.junit.jupiter.api.Test
    void defaultTenantUsesLegacyKeySoExistingCiphertextStillDecrypts() {
        var service = createService("master-key");

        // Encrypt with the legacy global path (no tenant), decrypt as DEFAULT.
        String legacy = service.encrypt("token");
        assertThat(service.decrypt(legacy, com.viglet.turing.persistence.model.tenant.TurTenant.DEFAULT_TENANT_ID))
                .isEqualTo("token");
    }
}
