package com.viglet.turing.connector.webcrawler.persistence.repository;

import com.viglet.turing.connector.webcrawler.persistence.model.TurWCFileExtension;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCNotAllowUrl;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TurWCNotAllowUrlRepository extends JpaRepository<TurWCNotAllowUrl, String> {
    List<TurWCNotAllowUrl> findByTurWCSource(TurWCSource turWCSource);
}
