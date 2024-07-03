package com.viglet.turing.connector.webcrawler.persistence.model;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Entity
public class TurWCNotAllowUrl extends TurWCUrl implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // bi-directional many-to-one association to TurWCSource
    @ManyToOne
    @JoinColumn(name = "wc_source_id", nullable = false)
    private TurWCSource turWCSource;

    public TurWCNotAllowUrl(String url, TurWCSource turWCSource) {
        this.url = url;
        this.turWCSource = turWCSource;
    }

    public TurWCNotAllowUrl() {
        super();
    }
}
