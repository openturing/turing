package com.viglet.turing.service.storage;

import com.viglet.core.storage.VigletNoOpStorageService;

/**
 * No-op storage for Turing ({@code turing.storage.type=none}) — a thin adapter
 * over the lifted {@link VigletNoOpStorageService} from
 * {@code viglet-core-storage} (Block Q / T368).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurNoOpStorageService extends TurStorageServiceAdapter {

    public TurNoOpStorageService() {
        super(new VigletNoOpStorageService());
    }
}
