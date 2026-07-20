/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.gateway.TurGatewayKey;
import com.viglet.turing.persistence.repository.gateway.TurGatewayKeyRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * T741 / §XLIX — mints, authenticates and reveals {@link TurGatewayKey} virtual
 * keys for the Governed LLM Gateway (Block AZ).
 *
 * <p>A key is {@code sk-turing-} + 32 random Base62 chars, generated with
 * {@link SecureRandom}, shown once. At rest only the SHA-256 {@code keyHash}
 * (authentication lookup) and the {@code TurSecretCryptoService}-encrypted raw
 * key (reveal) are stored. Presented keys are compared by constant-time hash
 * equality, never by decrypting.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurGatewayKeyService {

    /** All Turing virtual keys carry this prefix so they are recognisable and routable. */
    public static final String KEY_PREFIX = "sk-turing-";
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RANDOM_LEN = 32;

    private final TurGatewayKeyRepository repository;
    private final TurSecretCryptoService secretCryptoService;
    private final SecureRandom secureRandom = new SecureRandom();

    public TurGatewayKeyService(TurGatewayKeyRepository repository,
            TurSecretCryptoService secretCryptoService) {
        this.repository = repository;
        this.secretCryptoService = secretCryptoService;
    }

    /** The raw key returned exactly once at creation, paired with the persisted entity. */
    public record GeneratedKey(TurGatewayKey key, String rawKey) {
    }

    /**
     * Mints and persists a new virtual key. The returned {@code rawKey} is the
     * only time the full secret is available — the caller must surface it to the
     * operator immediately.
     */
    public GeneratedKey create(String name, String allowedModels, LocalDateTime expiresAt,
            String tenantId) {
        String rawKey = KEY_PREFIX + randomToken();
        TurGatewayKey key = new TurGatewayKey();
        key.setName(name == null || name.isBlank() ? "Gateway key" : name);
        key.setKeyHash(sha256Hex(rawKey));
        key.setKeyPrefix(displayPrefix(rawKey));
        key.setSecretEncrypted(secretCryptoService.encrypt(rawKey));
        key.setAllowedModels(allowedModels);
        key.setTenantId(tenantId);
        key.setEnabled(1);
        key.setExpiresAt(expiresAt);
        key.setCreationDate(LocalDateTime.now());
        key.setModificationDate(LocalDateTime.now());
        repository.save(key);
        log.info("[Gateway] Minted virtual key {} ({})", key.getId(), key.getKeyPrefix());
        return new GeneratedKey(key, rawKey);
    }

    /**
     * Authenticates a presented raw key: must carry the Turing prefix, hash to a
     * stored key, and that key must be {@link TurGatewayKey#isUsable() usable}.
     */
    public Optional<TurGatewayKey> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        return repository.findByKeyHash(sha256Hex(rawKey)).filter(TurGatewayKey::isUsable);
    }

    /** Decrypts and returns the raw key for an admin reveal/copy action. */
    public String reveal(TurGatewayKey key) {
        return secretCryptoService.decrypt(key.getSecretEncrypted());
    }

    /**
     * Rotates a key in place: mints a fresh secret, keeping scope/budget/id. The
     * new raw key is returned once.
     */
    public GeneratedKey rotate(TurGatewayKey key) {
        String rawKey = KEY_PREFIX + randomToken();
        key.setKeyHash(sha256Hex(rawKey));
        key.setKeyPrefix(displayPrefix(rawKey));
        key.setSecretEncrypted(secretCryptoService.encrypt(rawKey));
        key.setModificationDate(LocalDateTime.now());
        repository.save(key);
        return new GeneratedKey(key, rawKey);
    }

    /**
     * Whether {@code model} is within this key's {@code allowedModels} scope. A
     * blank allow-list means any model; a non-blank list requires a match (a
     * {@code null} requested model is rejected under a restricted key).
     */
    public boolean isModelAllowed(TurGatewayKey key, String model) {
        String allowed = key.getAllowedModels();
        if (allowed == null || allowed.isBlank()) {
            return true;
        }
        if (model == null || model.isBlank()) {
            return false;
        }
        return Arrays.stream(allowed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equalsIgnoreCase(model));
    }

    private String randomToken() {
        StringBuilder sb = new StringBuilder(RANDOM_LEN);
        for (int i = 0; i < RANDOM_LEN; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String displayPrefix(String rawKey) {
        // sk-turing- + first 6 random chars + a masked tail.
        int end = Math.min(rawKey.length(), KEY_PREFIX.length() + 6);
        return rawKey.substring(0, end) + "••••";
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
