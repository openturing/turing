package com.viglet.turing.client.sn.job;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.Map;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurSNJobAttributeSpec implements Serializable {
    private static final long serialVersionUID = 1L;
    protected String name;
    protected TurSEFieldType type;
    protected boolean mandatory;
    protected boolean multiValued;
    protected String description;
    protected boolean facet;
    protected Map<String, String> facetName;

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
