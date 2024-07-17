package com.viglet.turing.connector.webcrawler.persistence.repository;

import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAllowUrl;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurWCAllowUrlRepository extends JpaRepository<TurWCAllowUrl, String> {
    Optional<List<TurWCAllowUrl>> findByTurWCSource(TurWCSource turWCSource);
}
