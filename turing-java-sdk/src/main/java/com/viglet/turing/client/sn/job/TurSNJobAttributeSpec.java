package com.viglet.turing.client.sn.job;

import com.viglet.turing.commons.se.field.TurSEFieldType;

import java.io.Serializable;

public class TurSNJobAttributeSpec implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;
    protected TurSEFieldType type;
    protected boolean mandatory;
    protected boolean multiValued;
    protected String description;
    protected boolean facet;
    protected String facetName;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TurSEFieldType getType() {
        return type;
    }

    public void setType(TurSEFieldType type) {
        this.type = type;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isMultiValued() {
        return multiValued;
    }

    public void setMultiValued(boolean multiValued) {
        this.multiValued = multiValued;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isFacet() {
        return facet;
    }

    public void setFacet(boolean facet) {
        this.facet = facet;
    }

    public String getFacetName() {
        return facetName;
    }

    public void setFacetName(String facetName) {
        this.facetName = facetName;
    }

    @Override
    public String toString() {
        return "TurSNJobAttributeSpec{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", mandatory=" + mandatory +
                ", multiValued=" + multiValued +
                ", description='" + description + '\'' +
                ", facet=" + facet +
                ", facetName='" + facetName + '\'' +
                '}';
    }
}
