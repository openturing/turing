package com.viglet.turing.connector.aem.indexer.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "aem_source_attribute")
public class TurAemSourceAttribute implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    private String name;
    private String className;
    private String text;

    // bi-directional many-to-one association to TurAemTargetAttribute
    @ManyToOne
    @JoinColumn(name = "aem_target_attribute_id", nullable = false)
    private TurAemTargetAttribute turAemTargetAttribute;

}
