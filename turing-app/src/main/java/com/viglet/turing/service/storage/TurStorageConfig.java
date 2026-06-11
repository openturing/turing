package com.viglet.turing.service.storage;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the active {@link TurStorageService} bean based on {@code turing.storage.type}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Configuration
public class TurStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(TurStorageConfig.class);

    @Bean
    public TurStorageService turStorageService(TurConfigProperties configProperties,
            com.viglet.turing.tenant.TurTenantContext tenantContext) {
        TurStorageType type = resolveType(configProperties);
        log.info("Storage backend: {}", type);
        TurStorageService delegate = switch (type) {
            case MINIO -> new TurMinioStorageService(configProperties);
            case FILESYSTEM -> new TurFilesystemStorageService(configProperties);
            case NONE -> new TurNoOpStorageService();
        };
        // T269 / §XIV.4.3 — wrap real backends so every object key is scoped under
        // tenants/<tenantId>/. No-op when tenancy is off (empty prefix); pointless
        // for the disabled NoOp backend, so it stays unwrapped.
        if (type == TurStorageType.NONE) {
            return delegate;
        }
        return new TurTenantScopedStorageService(delegate, tenantContext);
    }

    static TurStorageType resolveType(TurConfigProperties config) {
        TurStorageProperty storage = config.getStorage();
        if (storage != null && storage.getType() != null) {
            return storage.getType();
        }
        return TurStorageType.NONE;
    }
}
