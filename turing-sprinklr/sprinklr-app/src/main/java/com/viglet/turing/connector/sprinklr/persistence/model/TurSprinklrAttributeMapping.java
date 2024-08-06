package com.viglet.turing.connector.sprinklr.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.spring.jpa.TurUuid;
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
public class TurSprinklrAttributeMapping implements Serializable {

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
    private TurSprinklrSource turSprinklrSource;

    public TurSprinklrAttributeMapping(String name, Class<?> className, TurSprinklrSource turSprinklrSource) {
        this.name = name;
        this.className = className.getName();
        this.text = null;
        this.turSprinklrSource = turSprinklrSource;
    }

    public TurSprinklrAttributeMapping(String name, String text, TurSprinklrSource turSprinklrSource) {
        this.name = name;
        this.className = null;
        this.text = text;
        this.turSprinklrSource = turSprinklrSource;
    }
}
