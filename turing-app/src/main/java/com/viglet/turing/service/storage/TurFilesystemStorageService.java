package com.viglet.turing.service.storage;

import com.viglet.core.storage.VigletFilesystemStorageService;
import com.viglet.turing.properties.TurConfigProperties;

import jakarta.annotation.PostConstruct;

/**
 * Filesystem-backed storage for Turing — a thin adapter over the lifted
 * {@link VigletFilesystemStorageService} from {@code viglet-core-storage}
 * (Block Q / T368). The filesystem logic lives in core; this class only maps
 * Turing's config + API types.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurFilesystemStorageService extends TurStorageServiceAdapter {

    private final TurConfigProperties configProperties;

    public TurFilesystemStorageService(TurConfigProperties configProperties) {
        super(new VigletFilesystemStorageService(TurStorageMapper.toCoreProperties(configProperties)));
        this.configProperties = configProperties;
    }

    @PostConstruct
    public void init() {
        // Re-read configuration at init time (the backing config bean may be
        // populated after construction), then initialize the backend.
        setDelegate(new VigletFilesystemStorageService(TurStorageMapper.toCoreProperties(configProperties)));
        initBackend();
    }
}
