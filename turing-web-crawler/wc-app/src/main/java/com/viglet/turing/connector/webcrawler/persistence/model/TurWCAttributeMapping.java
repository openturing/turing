package com.viglet.turing.connector.webcrawler.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "wc_attribute_mapping")
public class TurWCAttributeMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    private String name;
    private String className;
    private String text;

    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "ws_source_id", nullable = false)
    private TurWCSource turWCSource;

    public TurWCAttributeMapping() {
        super();
    }

    public TurWCAttributeMapping(String name, Class<?> className, TurWCSource turWCSource) {
        this.name = name;
        this.className = className.getName();
        this.text = null;
        this.turWCSource = turWCSource;
    }

    public TurWCAttributeMapping(String name, String text, TurWCSource turWCSource) {
        this.name = name;
        this.className = null;
        this.text = text;
        this.turWCSource = turWCSource;
    }
}
