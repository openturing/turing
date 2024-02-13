package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurAemSourceRepository extends JpaRepository<TurAemSource, String> {
}
