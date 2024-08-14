package com.viglet.turing.connector.webcrawler.persistence.repository;

import com.viglet.turing.connector.webcrawler.persistence.model.TurWCUrl;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurWCUrlRepository extends JpaRepository<TurWCUrl, String> {
}
