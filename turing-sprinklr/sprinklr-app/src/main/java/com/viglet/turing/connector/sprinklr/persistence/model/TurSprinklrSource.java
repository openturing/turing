package com.viglet.turing.connector.sprinklr.persistence.model;

import com.viglet.turing.spring.jpa.TurUuid;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
@Table(name = "sprinklr_source")
public class TurSprinklrSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private String environment;
    @Column
    private String apiKey;
    @Column
    private String secretKey;

    @Builder.Default
    @ElementCollection(targetClass = String.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "sprinklr_sn_site", joinColumns = @JoinColumn(name = "source_id"))
    @Column(name = "sn_site", nullable = false)
    private Collection<String> turSNSites = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "turSprinklrSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurSprinklrAttributeMapping> attributeMappings = new HashSet<>();

    public void setAttributeMappings(Collection<TurSprinklrAttributeMapping> attributeMappings) {
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
