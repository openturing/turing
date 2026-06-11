package com.viglet.turing.service.storage;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures the active {@link TurStorageService} bean based on {@code turing.storage.type}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Configuration
public class TurStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(TurStorageConfig.class);

    /**
     * The raw storage backend. Declared as its own {@code @Bean} (rather than
     * {@code new}-ed inside the wrapper factory) so Spring runs its
     * {@code @PostConstruct} lifecycle — e.g. {@code TurFilesystemStorageService.init()}
     * that sets {@code basePath} / flips {@code isEnabled()} true.
     */
    @Bean
    public TurStorageService turStorageBackend(TurConfigProperties configProperties) {
        TurStorageType type = resolveType(configProperties);
        log.info("Storage backend: {}", type);
        return switch (type) {
            case MINIO -> new TurMinioStorageService(configProperties);
            case FILESYSTEM -> new TurFilesystemStorageService(configProperties);
            case NONE -> new TurNoOpStorageService();
        };
    }

    /**
     * T269 / §XIV.4.3 — the primary {@link TurStorageService}: wraps the backend
     * so every object key is scoped under {@code tenants/<tenantId>/}. No-op when
     * tenancy is off (empty prefix); the disabled NoOp backend is left unwrapped.
     */
    @Bean
    @Primary
    public TurStorageService turStorageService(TurStorageService turStorageBackend,
            com.viglet.turing.tenant.TurTenantContext tenantContext) {
        if (turStorageBackend.getType() == TurStorageType.NONE) {
            return turStorageBackend;
        }
        return new TurTenantScopedStorageService(turStorageBackend, tenantContext);
    }

    static TurStorageType resolveType(TurConfigProperties config) {
        TurStorageProperty storage = config.getStorage();
        if (storage != null && storage.getType() != null) {
            return storage.getType();
        }
        return TurStorageType.NONE;
    }
}
