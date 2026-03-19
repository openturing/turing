package com.viglet.turing.persistence.repository.asset;

import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public interface TurAssetTrainingRecordRepository extends JpaRepository<TurAssetTrainingRecord, String> {
    List<TurAssetTrainingRecord> findByObjectNameIn(Collection<String> objectNames);
    List<TurAssetTrainingRecord> findByFileNameContainingIgnoreCase(String keyword);
    List<TurAssetTrainingRecord> findByContentType(String contentType);
    long countByContentType(String contentType);
}
