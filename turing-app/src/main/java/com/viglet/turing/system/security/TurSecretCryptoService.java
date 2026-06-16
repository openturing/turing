package com.viglet.turing.system.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.viglet.core.crypto.VigletSecretCryptoService;
import com.viglet.turing.tenant.TurTenantContext;

/**
 * Turing's secret-crypto bean: the {@code viglet-core} AES/GCM primitive
 * ({@link VigletSecretCryptoService}) wired with Turing's tenant context and
 * legacy key material.
 *
 * <p>The crypto logic itself now lives in {@code viglet-core-crypto} (Block Q /
 * T368). This subclass preserves Turing's behaviour exactly — the
 * {@code turing.ai.crypto.key} property, the production fail-fast, the
 * {@link #LEGACY_SALT_PREFIX legacy salt prefix} and dev fallback — so all
 * previously-encrypted secrets stay decryptable. As a {@code @Service} bean it
 * satisfies the core auto-configuration's {@code @ConditionalOnMissingBean},
 * so the generic {@code viglet.crypto.*} bean never activates here.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurSecretCryptoService extends VigletSecretCryptoService {

    /**
     * Kept stable from the pre-extraction implementation so per-tenant
     * ciphertext derived under the old salt remains decryptable.
     */
    private static final String LEGACY_SALT_PREFIX = "turing-tenant-secret:";

    @Autowired
    public TurSecretCryptoService(Environment environment,
            @Value("${turing.ai.crypto.key:}") String configuredKey,
            TurTenantContext tenantContext) {
        super(new TurCryptoKeyMaterial(environment, configuredKey), LEGACY_SALT_PREFIX,
                new TurTenantKeyResolver(tenantContext));
    }

    /** Legacy 2-arg constructor (tests / non-tenant contexts) — no tenant derivation. */
    public TurSecretCryptoService(Environment environment, String configuredKey) {
        super(new TurCryptoKeyMaterial(environment, configuredKey), LEGACY_SALT_PREFIX,
                new TurTenantKeyResolver(null));
    }
}
