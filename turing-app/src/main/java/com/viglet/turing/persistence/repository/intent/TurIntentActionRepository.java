package com.viglet.turing.persistence.repository.intent;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.intent.TurIntentAction;

/**
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
public interface TurIntentActionRepository extends JpaRepository<TurIntentAction, String> {
}
