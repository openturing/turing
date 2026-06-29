package com.viglet.turing.persistence.repository.intent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.intent.TurIntent;

/**
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
public interface TurIntentRepository extends JpaRepository<TurIntent, String> {

    @Modifying
    @Query("delete from TurIntent i where i.id = ?1")
    void delete(String id);

    List<TurIntent> findByEnabledOrderBySortOrderAsc(int enabled);

    List<TurIntent> findByTurAIAgent_IdOrderByTitleAsc(String agentId);

    List<TurIntent> findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc(String agentId, int enabled);
}
