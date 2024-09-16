package com.viglet.turing.connector.cms.mappers;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TurCmsTargetAttr extends TurCmsTargetAttrDefinition{
    private String textValue;
    protected List<TurCmsSourceAttr> sourceAttrs = new ArrayList<>();

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
