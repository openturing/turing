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
@Table(name = "wc_file_extension")
public class TurWCFileExtension implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    private String extension;

    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "ws_source_id", nullable = false)
    private TurWCSource turWCSource;

    public TurWCFileExtension() {
        super();
    }

    public TurWCFileExtension(String extension, TurWCSource turWCSource) {
        this.extension = extension;
        this.turWCSource = turWCSource;
    }
}
