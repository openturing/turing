package com.viglet.turing.persistence.model.intent;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an action (prompt item) within an Intent category.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Getter
@Setter
@Entity
@Table(name = "intent_action")
public class TurIntentAction implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 150)
    private String label;

    @Lob
    @Column(name = "prompt", columnDefinition = "longtext")
    private String prompt;

    @Column(name = "sortOrder")
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intent_id", nullable = false)
    @JsonBackReference
    private TurIntent turIntent;
}
