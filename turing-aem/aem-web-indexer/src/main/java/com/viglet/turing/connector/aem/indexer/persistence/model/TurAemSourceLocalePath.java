package com.viglet.turing.connector.aem.indexer.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;

@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "aem_source_locale_path")
@JsonIgnoreProperties({ "turAemSource" })
public class TurAemSourceLocalePath implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    private String id;
    @Column
    private Locale locale;
    @Column
    private String path;
    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "aem_source_id", nullable = false)
    private TurAemSource turAemSource;

}