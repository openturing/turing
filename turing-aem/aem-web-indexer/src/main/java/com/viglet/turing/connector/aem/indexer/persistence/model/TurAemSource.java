package com.viglet.turing.connector.aem.indexer.persistence.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UuidGenerator;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;


@Entity
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Table(name = "aem_source")
public class TurAemSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @UuidGenerator
    private String id;
    @Column
    private String url;
    @Column
    private String username;
    @Column
    private String password;
    @Column
    private String rootPath;
    @Column
    private String contentType;
    @Column
    private String subType;
    @Column
    private String turSNSite;
    @Column
    private String siteName;
    @Column
    private Locale defaultLocale;
    @Column
    private String providerName;
    @Column
    private String group;
    @Column
    private String urlPrefix;
    @Column
    private String oncePattern;
    @Lob
    @Column
    private String mappingJson;

    // bi-directional many-to-one association to TurAemSourceLocalePath
    @Builder.Default
    @OneToMany(mappedBy = "turAemSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurAemSourceLocalePath> localePaths = new HashSet<>();

    // bi-directional many-to-one association to TurAemTargetAttribute
    @Builder.Default
    @OneToMany(mappedBy = "turAemSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurAemTargetAttribute> attributeMappings = new HashSet<>();

    public void setLocalePaths(Collection<TurAemSourceLocalePath> localePaths) {
        this.localePaths.clear();
        if (localePaths != null) {
            this.localePaths.addAll(localePaths);
        }
    }
}