package com.viglet.turing.connector.webcrawler.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

@Getter
@Setter
@Entity
@Table(name = "wc_source")
public class TurWCSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false)
    private String id;

    // bi-directional many-to-one association to turWCUrl
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCAllowUrl> allowUrls  = new HashSet<>();

    // bi-directional many-to-one association to turWCUrl
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCNotAllowUrl> notAllowUrls = new HashSet<>();

    // bi-directional many-to-one association to turWCFileExtension
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCFileExtension> notAllowExtensions = new HashSet<>();

    // bi-directional many-to-one association to turWCAttributeMapping
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCAttributeMapping> attributeMappings = new HashSet<>();

    private Locale locale;

    private String localeClass;

    private String url;

    private String turSNSite;

    private String username;

    private String password;
}
