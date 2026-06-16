package com.viglet.turing.system;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.viglet.core.settings.VigletConfigVarStore;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

/**
 * Turing's persistence adapter for the {@code viglet-core}
 * {@link VigletConfigVarStore} port — backs the shared
 * {@link com.viglet.core.settings.VigletConfigVarService} with the
 * {@link TurConfigVar} table via {@link TurConfigVarRepository}, preserving the
 * repository's cache eviction on writes.
 *
 * <p>The upsert in {@link #put} keeps the historical
 * find-or-create-then-stamp-path behavior so an existing row's other columns
 * survive. {@link #findByPath} filters the (cached) {@code findAll} rather than
 * adding a new {@code @Cacheable} query.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurConfigVarStore implements VigletConfigVarStore {

    private final TurConfigVarRepository turConfigVarRepository;

    public TurConfigVarStore(TurConfigVarRepository turConfigVarRepository) {
        this.turConfigVarRepository = turConfigVarRepository;
    }

    @Override
    public Optional<String> find(String id) {
        return turConfigVarRepository.findById(id).map(TurConfigVar::getValue);
    }

    @Override
    public void put(String id, String path, String value) {
        TurConfigVar configVar = turConfigVarRepository.findById(id).orElseGet(TurConfigVar::new);
        configVar.setId(id);
        configVar.setPath(path);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
    }

    @Override
    public Map<String, String> findByPath(String path) {
        Map<String, String> out = new LinkedHashMap<>();
        for (TurConfigVar configVar : turConfigVarRepository.findAll()) {
            if (path != null && path.equals(configVar.getPath())) {
                out.put(configVar.getId(), configVar.getValue());
            }
        }
        return out;
    }

    @Override
    public boolean exists(String id) {
        return turConfigVarRepository.findById(id).isPresent();
    }

    @Override
    public void remove(String id) {
        turConfigVarRepository.findById(id).ifPresent(turConfigVarRepository::delete);
    }
}
