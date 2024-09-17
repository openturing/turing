package com.viglet.turing.connector.aem.persistence.repository;

import com.viglet.turing.connector.aem.persistence.model.TurAemAttributeMapping;
import com.viglet.turing.connector.aem.persistence.model.TurAemSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurAemAttributeMappingRepository extends JpaRepository<TurAemAttributeMapping, String> {
    Optional<List<TurAemAttributeMapping>> findByTurAemSource(TurAemSource turWCSource);
}
