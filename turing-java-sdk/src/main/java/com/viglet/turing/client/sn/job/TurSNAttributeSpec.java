package com.viglet.turing.client.sn.job;

import com.viglet.turing.commons.se.field.TurSEFieldType;

import java.io.Serializable;

public class TurSNAttributeSpec  extends TurSNJobAttributeSpec implements Serializable {
    private static final long serialVersionUID = 1L;
    private String className;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @Override
    public String toString() {
        return "TurSNAttributeSpec{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", mandatory=" + mandatory +
                ", multiValued=" + multiValued +
                ", description='" + description + '\'' +
                ", facet=" + facet +
                ", facetName='" + facetName + '\'' +
                ", className='" + className + '\'' +
                '}';
    }
}
