/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Supplier;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES/GCM secret encryption with optional per-tenant key derivation.
 *
 * <p>Lifted from Turing's {@code TurSecretCryptoService} into {@code viglet-core}
 * (Block Q / T368) as the lowest-risk reusable security primitive. It is a plain
 * object — no Spring, no product types: where the key material comes from is a
 * {@link Supplier} and which tenant a secret belongs to is a
 * {@link VigletTenantKeyResolver}, both injected by the consumer.
 *
 * <ul>
 *   <li>The <em>default</em> tenant (per the resolver) uses a global key derived
 *       as {@code SHA-256(material)} — preserving any ciphertext written before
 *       per-tenant keys existed.</li>
 *   <li>A non-default tenant uses a key derived with {@code PBKDF2WithHmacSHA256}
 *       over {@code material} salted with {@code tenantSaltPrefix + tenantId}, so
 *       one tenant's leaked ciphertext is undecryptable under another's key.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletSecretCryptoService {

    /** Neutral default salt prefix; consumers with legacy ciphertext pass their own. */
    public static final String DEFAULT_TENANT_SALT_PREFIX = "viglet-tenant-secret:";

    private static final String CRYPTO_ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_SIZE = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int AES_KEY_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Supplier<String> keyMaterialSupplier;
    private final String tenantSaltPrefix;
    private final VigletTenantKeyResolver tenantResolver;

    /**
     * @param keyMaterialSupplier resolves the master key material on each
     *        operation (and may throw, e.g. when a key is mandatory but absent)
     * @param tenantSaltPrefix    salt prefix for per-tenant derivation; must stay
     *        stable for already-encrypted data to remain decryptable
     * @param tenantResolver      supplies the current tenant and the default-tenant test
     */
    public VigletSecretCryptoService(Supplier<String> keyMaterialSupplier,
            String tenantSaltPrefix,
            VigletTenantKeyResolver tenantResolver) {
        this.keyMaterialSupplier = keyMaterialSupplier;
        this.tenantSaltPrefix = tenantSaltPrefix != null ? tenantSaltPrefix : DEFAULT_TENANT_SALT_PREFIX;
        this.tenantResolver = tenantResolver != null ? tenantResolver : VigletTenantKeyResolver.SINGLE_TENANT;
    }

    /** Convenience: neutral salt prefix and {@link VigletTenantKeyResolver#SINGLE_TENANT}. */
    public VigletSecretCryptoService(Supplier<String> keyMaterialSupplier) {
        this(keyMaterialSupplier, DEFAULT_TENANT_SALT_PREFIX, VigletTenantKeyResolver.SINGLE_TENANT);
    }

    public String encrypt(String plainText) {
        return encrypt(plainText, tenantResolver.currentTenantId());
    }

    /** Encrypt with the key derived for {@code tenantId} (see class javadoc). */
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
        return decrypt(encryptedText, tenantResolver.currentTenantId());
    }

    /** Decrypt with the key derived for {@code tenantId}. */
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

    private SecretKeySpec resolveKey(String tenantId) {
        String material = keyMaterialSupplier.get();
        boolean perTenant = tenantId != null && !tenantResolver.isDefaultTenant(tenantId);
        try {
            byte[] key;
            if (perTenant) {
                PBEKeySpec spec = new PBEKeySpec(material.toCharArray(),
                        (tenantSaltPrefix + tenantId).getBytes(StandardCharsets.UTF_8),
                        PBKDF2_ITERATIONS, AES_KEY_BITS);
                key = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
            } else {
                key = MessageDigest.getInstance("SHA-256")
                        .digest(material.getBytes(StandardCharsets.UTF_8));
            }
            return new SecretKeySpec(key, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to initialize encryption key", e);
        }
    }
}
