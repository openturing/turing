package com.viglet.turing.connector.aem.persistence.model;

import com.viglet.turing.spring.jpa.TurUuid;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "sprinklr_attribute_mapping")
@JsonIgnoreProperties({ "turWCSource" })
public class TurAemAttributeMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String name;
    private String className;
    private String text;

    @ManyToOne
    @JoinColumn(name = "sprinklr_source_id", nullable = false)
    private TurAemSource turAemSource;

    public TurAemAttributeMapping(String name, Class<?> className, TurAemSource turAemSource) {
        this.name = name;
        this.className = className.getName();
        this.text = null;
        this.turAemSource = turAemSource;
    }

    public TurAemAttributeMapping(String name, String text, TurAemSource turAemSource) {
        this.name = name;
        this.className = null;
        this.text = text;
        this.turAemSource = turAemSource;
    }
}
