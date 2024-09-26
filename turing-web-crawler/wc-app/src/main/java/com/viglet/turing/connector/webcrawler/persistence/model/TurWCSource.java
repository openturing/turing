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
    public static final String TUR_WC_SOURCE = "turWCSource";
    public static final String SOURCE_ID = "source_id";
    public static final String WC_SN_SITE = "wc_sn_site";
    public static final String SN_SITE = "sn_site";

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;
    @Column
    private String title;
    @Column
    private String description;
    @Column
    private Locale locale;
    @Column
    private String localeClass;
    @Column
    private String url;
    @Column
    private String username;
    @Column
    private String password;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = WC_SN_SITE, joinColumns = @JoinColumn(name = SOURCE_ID))
    @Column(name = SN_SITE, nullable = false)
    private Collection<String> turSNSites = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCAllowUrl> allowUrls  = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCNotAllowUrl> notAllowUrls = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCFileExtension> notAllowExtensions = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = TUR_WC_SOURCE, orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurWCAttributeMapping> attributeMappings = new HashSet<>();

    public void setAllowUrls(Collection<TurWCAllowUrl> allowUrls) {
        this.allowUrls.clear();
        if (allowUrls != null) {
            this.allowUrls.addAll(allowUrls);
        }
    }

    public void setNotAllowUrls(Collection<TurWCNotAllowUrl> notAllowUrls) {
        this.notAllowUrls.clear();
        if (notAllowUrls != null) {
            this.notAllowUrls.addAll(notAllowUrls);
        }
    }
    public void setNotAllowExtensions(Collection<TurWCFileExtension> notAllowExtensions) {
        this.notAllowExtensions.clear();
        if (notAllowExtensions != null) {
            this.notAllowExtensions.addAll(notAllowExtensions);
        }
    }

    public void setAttributeMappings(Collection<TurWCAttributeMapping> attributeMappings) {
        this.attributeMappings.clear();
        if (attributeMappings != null) {
            this.attributeMappings.addAll(attributeMappings);
        }
    }
    public void setTurSNSites(Collection<String> turSNSites) {
        this.turSNSites.clear();
        if (turSNSites != null) {
            this.turSNSites.addAll(turSNSites);
        }
    }
}
