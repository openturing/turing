package com.viglet.turing.connector.aem.indexer.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

@RequiredArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name = "aem_source")
public class TurAemSource implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    private String url;

    private String username;

    private String password;

    private String rootPath;

    private String contentType;

    private String subType;

    private String turSNSite;

    private String siteName;

    // bi-directional many-to-one association to turAemAttributeMapping
    @OneToMany(mappedBy = "turAemSource", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurAemTargetAttribute> attributeMappings = new HashSet<>();

}