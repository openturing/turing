package com.viglet.turing.client.sn.job;

import lombok.*;

import java.io.Serializable;

@Setter
@Getter
public class TurSNAttributeSpec  extends TurSNJobAttributeSpec implements Serializable {
    private static final long serialVersionUID = 1L;
    private String className;

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
