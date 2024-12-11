package com.viglet.turing.connector.plugin.webcrawler.persistence.repository;

import com.viglet.turing.connector.plugin.webcrawler.persistence.model.TurWCSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurWCSourceRepository  extends JpaRepository<TurWCSource, String> {
}
