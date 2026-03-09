package com.viglet.turing.sn.template;

import java.util.Set;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

public record TurSNFieldDefinition(
                String name,
                String description,
                TurSEFieldType type,
                int multiValued,
                String facetName,
                Set<TurSNSiteFieldExtFacet> locales,
                int hl) {
}