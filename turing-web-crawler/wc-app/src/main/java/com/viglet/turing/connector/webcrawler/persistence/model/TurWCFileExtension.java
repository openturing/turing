package com.viglet.turing.connector.webcrawler.persistence.model;

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
@Table(name = "wc_file_extension")
@JsonIgnoreProperties({ "turWCSource" })
public class TurWCFileExtension implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String extension;

    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "ws_source_id", nullable = false)
    private TurWCSource turWCSource;

    public TurWCFileExtension(String extension, TurWCSource turWCSource) {
        this.extension = extension;
        this.turWCSource = turWCSource;
    }
}
