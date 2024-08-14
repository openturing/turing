package com.viglet.turing.connector.sprinklr.persistence.repository;

import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrAttributeMapping;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TurSprinklrAttributeMappingRepository extends JpaRepository<TurSprinklrAttributeMapping, String> {
    Optional<List<TurSprinklrAttributeMapping>> findByTurSprinklrSource(TurSprinklrSource turWCSource);
}
