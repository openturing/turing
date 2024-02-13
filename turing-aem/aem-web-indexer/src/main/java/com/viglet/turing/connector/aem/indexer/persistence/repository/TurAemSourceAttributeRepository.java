package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSourceAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurAemSourceAttributeRepository extends JpaRepository<TurAemSourceAttribute, String> {
}
