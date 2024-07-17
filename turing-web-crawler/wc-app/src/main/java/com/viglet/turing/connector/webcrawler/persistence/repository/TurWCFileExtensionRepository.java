package com.viglet.turing.connector.webcrawler.persistence.repository;

import com.viglet.turing.connector.webcrawler.persistence.model.TurWCFileExtension;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurWCFileExtensionRepository extends JpaRepository<TurWCFileExtension, String> {
    Optional<List<TurWCFileExtension>> findByTurWCSource(TurWCSource turWCSource);
}
