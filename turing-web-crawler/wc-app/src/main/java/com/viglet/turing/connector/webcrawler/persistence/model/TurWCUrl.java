package com.viglet.turing.connector.webcrawler.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Entity
@Table(name = "wc_url")
public class TurWCUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    protected String id;
    protected String url;
}
