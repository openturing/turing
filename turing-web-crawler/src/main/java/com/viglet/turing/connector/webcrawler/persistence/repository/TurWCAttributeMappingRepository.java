package com.viglet.turing.connector.webcrawler.persistence.repository;

import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAttributeMapping;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCNotAllowUrl;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurWCAttributeMappingRepository extends JpaRepository<TurWCAttributeMapping, String> {
    List<TurWCAttributeMapping> findByTurWCSource(TurWCSource turWCSource);
}
