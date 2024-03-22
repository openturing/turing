package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemConfigVar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurAemConfigVarRepository extends JpaRepository<TurAemConfigVar, String> {
}
