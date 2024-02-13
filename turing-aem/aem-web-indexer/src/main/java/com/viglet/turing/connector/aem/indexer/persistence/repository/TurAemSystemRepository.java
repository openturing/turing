package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSystem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TurAemSystemRepository extends JpaRepository<TurAemSystem, String> {
    Optional<TurAemSystem> findByConfig(String config);
}
