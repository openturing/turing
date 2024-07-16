package com.viglet.turing.connector.webcrawler.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.jpa.TurUuid;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;


@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wc_attribute_mapping")
@JsonIgnoreProperties({ "turWCSource" })
public class TurWCAttributeMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String name;
    private String className;
    private String text;

    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "ws_source_id", nullable = false)
    private TurWCSource turWCSource;

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
