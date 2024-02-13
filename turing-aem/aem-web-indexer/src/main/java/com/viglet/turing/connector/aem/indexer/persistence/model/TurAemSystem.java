package com.viglet.turing.connector.aem.indexer.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@RequiredArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name = "aem_system", uniqueConstraints = {@UniqueConstraint(columnNames = {"config"})})
public class TurAemSystem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String config;
    @Column
    private String stringValue;
    @Column
    private boolean booleanValue;

    public TurAemSystem(String config, String value) {
        this.config = config;
        this.stringValue = value;
    }

    public TurAemSystem(String config, boolean value) {
        this.config = config;
        this.booleanValue = value;
    }
}