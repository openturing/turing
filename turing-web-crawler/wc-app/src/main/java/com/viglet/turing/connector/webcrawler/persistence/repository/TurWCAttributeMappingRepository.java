package com.viglet.turing.connector.webcrawler.persistence.repository;

import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAttributeMapping;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurWCAttributeMappingRepository extends JpaRepository<TurWCAttributeMapping, String> {
    Optional<List<TurWCAttributeMapping>> findByTurWCSource(TurWCSource turWCSource);
}
