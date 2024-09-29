package com.viglet.turing.client.sn.job;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
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
