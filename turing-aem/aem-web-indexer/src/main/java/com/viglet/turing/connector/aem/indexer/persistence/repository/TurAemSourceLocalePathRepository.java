package com.viglet.turing.connector.aem.indexer.persistence.repository;

import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSourceLocalePath;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurAemSourceLocalePathRepository extends JpaRepository<TurAemSourceLocalePath, String> {
}
