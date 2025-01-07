package com.viglet.turing.connector.plugin.aem.persistence.model;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.spring.jpa.TurUuid;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "aem_attribute_specification")
@JsonIgnoreProperties({ "turAemSource" })
public class TurAemAttributeSpecification extends TurSNAttributeSpec implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurUuid
    @Column(name = "id", nullable = false)
    private String id;

    private String className;
    private String text;

    @Builder.Default
    @ElementCollection
    @MapKeyColumn(name="language")
    @Column(name="facet_name")
    @CollectionTable(name="aem_attritbute_facet", joinColumns=@JoinColumn(name="spec_id"))
    private Map<String, String> facetNames = new HashMap<>();

    @ManyToOne
    @JoinColumn(name = "aem_source_id", nullable = false)
    private TurAemSource turAemSource;

}
