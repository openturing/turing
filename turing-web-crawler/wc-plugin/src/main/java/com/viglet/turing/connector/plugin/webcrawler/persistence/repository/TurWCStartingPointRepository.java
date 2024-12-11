package com.viglet.turing.connector.plugin.webcrawler.persistence.repository;

import com.viglet.turing.connector.plugin.webcrawler.persistence.model.TurWCSource;
import com.viglet.turing.connector.plugin.webcrawler.persistence.model.TurWCStartingPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurWCStartingPointRepository extends JpaRepository<TurWCStartingPoint, String> {
    Optional<List<TurWCStartingPoint>> findByTurWCSource(TurWCSource turWCSource);
}
