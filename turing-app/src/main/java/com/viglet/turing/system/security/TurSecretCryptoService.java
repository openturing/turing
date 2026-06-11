package com.viglet.turing.system.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.tenant.TurTenantContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurSecretCryptoService {

    private static final String CRYPTO_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String DEV_FALLBACK_KEY = "turing-dev-insecure-default-key";
    // T271 / §XIV.4.5 — PBKDF2 parameters for per-tenant key derivation.
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int AES_KEY_BITS = 256;
    private static final String PBKDF2_SALT_PREFIX = "turing-tenant-secret:";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    private final String configuredKey;
    private final TurTenantContext tenantContext;
    private boolean warnedAboutFallback = false;

    @Autowired
    public TurSecretCryptoService(Environment environment,
            @Value("${turing.ai.crypto.key:}") String configuredKey,
            TurTenantContext tenantContext) {
        this.environment = environment;
        this.configuredKey = configuredKey;
        this.tenantContext = tenantContext;
    }

    /** Legacy 2-arg constructor (tests / non-tenant contexts) — no tenant derivation. */
    public TurSecretCryptoService(Environment environment, String configuredKey) {
        this(environment, configuredKey, null);
    }

    public String encrypt(String plainText) {
        return encrypt(plainText, currentTenant());
    }

    /**
     * T271 / §XIV.4.5 — encrypt with a key derived for {@code tenantId}. For the
     * {@code DEFAULT} tenant (or {@code null}) the legacy global key is used, so
     * existing ciphertexts stay decryptable.
     */
    public String encrypt(String plainText, String tenantId) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(tenantId), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length);
            payload.put(iv);
            payload.put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt provider secret", e);
        }
    }

    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, currentTenant());
    }

    /** Decrypt with the key derived for {@code tenantId} (see {@link #encrypt(String, String)}). */
    public String decrypt(String encryptedText, String tenantId) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return null;
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= IV_SIZE) {
                throw new IllegalArgumentException("Encrypted payload is invalid");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_SIZE);
            byte[] cipherText = Arrays.copyOfRange(payload, IV_SIZE, payload.length);

            Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(tenantId), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt provider secret", e);
        }
    }

    private String currentTenant() {
        return tenantContext != null ? tenantContext.resolveCurrentTenant() : null;
    }

    private SecretKeySpec resolveKey(String tenantId) {
        String material = resolveMaterial();
        boolean perTenant = tenantId != null && !TurTenant.DEFAULT_TENANT_ID.equals(tenantId);
        try {
            byte[] key;
            if (perTenant) {
                // Derive a tenant-specific AES key: a leaked ciphertext from one
                // tenant is not decryptable under another's derived key.
                PBEKeySpec spec = new PBEKeySpec(material.toCharArray(),
                        (PBKDF2_SALT_PREFIX + tenantId).getBytes(StandardCharsets.UTF_8),
                        PBKDF2_ITERATIONS, AES_KEY_BITS);
                key = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
            } else {
                // Legacy global key — preserves existing DEFAULT-tenant ciphertexts.
                key = MessageDigest.getInstance("SHA-256")
                        .digest(material.getBytes(StandardCharsets.UTF_8));
            }
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialize encryption key", e);
        }
    }

    private String resolveMaterial() {
        String material = configuredKey;
        if (material == null || material.isBlank()) {
            if (isProductionProfileActive()) {
                throw new IllegalStateException("turing.ai.crypto.key must be configured in production");
            }
            if (!warnedAboutFallback) {
                warnedAboutFallback = true;
                log.warn("Using insecure fallback key for AI secret encryption outside production");
            }
            material = DEV_FALLBACK_KEY;
        }
        return material;
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
    }
}
