package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemTargetAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurAemTargetAttributeRepository extends JpaRepository<TurAemTargetAttribute, String> {
}
