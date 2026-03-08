package com.viglet.turing.system.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurSecretCryptoService {

    private static final String CRYPTO_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String DEV_FALLBACK_KEY = "turing-dev-insecure-default-key";

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;
    private final String configuredKey;
    private boolean warnedAboutFallback = false;

    public TurSecretCryptoService(Environment environment,
            @Value("${turing.ai.crypto.key:}") String configuredKey) {
        this.environment = environment;
        this.configuredKey = configuredKey;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_SIZE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CRYPTO_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, resolveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
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
            cipher.init(Cipher.DECRYPT_MODE, resolveKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt provider secret", e);
        }
    }

    private SecretKeySpec resolveKey() {
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

        try {
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialize encryption key", e);
        }
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("production"::equalsIgnoreCase);
    }
}
