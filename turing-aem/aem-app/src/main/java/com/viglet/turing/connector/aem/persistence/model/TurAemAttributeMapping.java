package com.viglet.turing.connector.aem.persistence.model;

import com.viglet.turing.connector.aem.export.bean.TurAemFacetExchange;
import com.viglet.turing.spring.jpa.TurUuid;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;


@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "aem_attribute_mapping")
@JsonIgnoreProperties({ "turAemSource" })
public class TurAemAttributeMapping implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String name;
    private String className;
    private String text;
    private String facetNameDefault;
    @Builder.Default
    @OneToMany(mappedBy = "turAemAttributeMapping", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({org.hibernate.annotations.CascadeType.ALL, org.hibernate.annotations.CascadeType.DELETE_ORPHAN})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Collection<TurAemAttributeFacet> facetNames = new HashSet<>();
    @ManyToOne
    @JoinColumn(name = "aem_source_id", nullable = false)
    private TurAemSource turAemSource;
}
