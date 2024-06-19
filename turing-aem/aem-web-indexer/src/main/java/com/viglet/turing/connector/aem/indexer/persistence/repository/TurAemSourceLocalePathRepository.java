package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSourceLocalePath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface TurAemSourceLocalePathRepository extends JpaRepository<TurAemSourceLocalePath, String> {
    Optional<Collection<TurAemSourceLocalePath>> findByTurAemSource(TurAemSource turAemSource);
}
