package com.viglet.turing.service.storage;

import com.viglet.core.storage.VigletMinioStorageService;
import com.viglet.turing.properties.TurConfigProperties;

import jakarta.annotation.PostConstruct;

/**
 * MinIO-backed storage for Turing — a thin adapter over the lifted
 * {@link VigletMinioStorageService} from {@code viglet-core-storage}
 * (Block Q / T368). The MinIO logic lives in core; this class only maps
 * Turing's config + API types.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurMinioStorageService extends TurStorageServiceAdapter {

    private final TurConfigProperties configProperties;

    public TurMinioStorageService(TurConfigProperties configProperties) {
        super(new VigletMinioStorageService(TurStorageMapper.toCoreProperties(configProperties)));
        this.configProperties = configProperties;
    }

    @PostConstruct
    public void init() {
        // Re-read configuration at init time (the backing config bean may be
        // populated after construction), then initialize the backend.
        setDelegate(new VigletMinioStorageService(TurStorageMapper.toCoreProperties(configProperties)));
        initBackend();
    }
}
