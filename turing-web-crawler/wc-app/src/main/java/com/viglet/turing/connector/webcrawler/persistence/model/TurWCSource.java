package com.viglet.turing.connector.webcrawler.persistence.model;

import com.viglet.turing.spring.jpa.TurUuid;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "wc_source")

public class TurWCSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private Locale locale;

    private String localeClass;

    private String url;

    private String turSNSite;

    private String username;

    private String password;
    @Builder.Default
    // bi-directional many-to-one association to turWCUrl
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCAllowUrl> allowUrls  = new HashSet<>();

    @Builder.Default
    // bi-directional many-to-one association to turWCUrl
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCNotAllowUrl> notAllowUrls = new HashSet<>();

    @Builder.Default
    // bi-directional many-to-one association to turWCFileExtension
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCFileExtension> notAllowExtensions = new HashSet<>();

    @Builder.Default
    // bi-directional many-to-one association to turWCAttributeMapping
    @OneToMany(mappedBy = "turWCSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCAttributeMapping> attributeMappings = new HashSet<>();
}
