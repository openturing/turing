package com.viglet.turing.connector.aem.commons.mappers;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TurAemTargetAttr extends TurAemTargetAttrDefinition {
    private String textValue;
    protected List<TurAemSourceAttr> sourceAttrs = new ArrayList<>();

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
