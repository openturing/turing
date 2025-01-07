package com.viglet.turing.connector.plugin.aem.persistence.repository;

import com.viglet.turing.connector.plugin.aem.persistence.model.TurAemAttributeSpecification;
import com.viglet.turing.connector.plugin.aem.persistence.model.TurAemSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurAemAttributeSpecificationRepository extends JpaRepository<TurAemAttributeSpecification, String> {
    Optional<List<TurAemAttributeSpecification>> findByTurAemSource(TurAemSource turAemSource);
    Optional<TurAemAttributeSpecification> findByTurAemSourceAndName(TurAemSource turAemSource, String name);
}
