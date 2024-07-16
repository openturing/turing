package com.viglet.turing.connector.webcrawler.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@JsonIgnoreProperties({ "turWCSource" })
public class TurWCAllowUrl extends TurWCUrl implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "wc_source_id", nullable = false)
    private TurWCSource turWCSource;

    public TurWCAllowUrl(String url, TurWCSource turWCSource) {
        this.url = url;
        this.turWCSource = turWCSource;
    }
}
