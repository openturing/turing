package com.viglet.turing.connector.aem.commons.mappers;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TurAemTargetAttr extends TurAemTargetAttrDefinition {
    private String textValue;
    protected List<TurAemSourceAttr> sourceAttrs;

    @Override
    public String toString() {
        return "{" +
                "name='" + getName() + '\'' +
                ", type='" + getType() + '\'' +
                ", mandatory=" + isMandatory() +
                ", multiValued=" + isMultiValued() +
                ", description='" + getDescription() + '\'' +
                ", facet=" + isFacet() +
                ", facetName='" + getFacetName() + '\'' +
                ", className='" + getClassName() + '\'' +
                ", textValue='" + getTextValue() + '\'' +
                ", sourceAttrs='" + getSourceAttrs() + '\'' +
                '}';
    }
}
