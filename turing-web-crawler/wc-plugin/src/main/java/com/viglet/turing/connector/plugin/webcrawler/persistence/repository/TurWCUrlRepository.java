package com.viglet.turing.connector.plugin.webcrawler.persistence.repository;

import com.viglet.turing.connector.plugin.webcrawler.persistence.model.TurWCUrl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurWCUrlRepository extends JpaRepository<TurWCUrl, String> {
}
