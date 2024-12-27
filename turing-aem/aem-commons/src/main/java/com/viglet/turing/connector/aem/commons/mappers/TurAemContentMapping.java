package com.viglet.turing.connector.aem.commons.mappers;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurAemContentMapping {
    private List<TurSNAttributeSpec> targetAttrDefinitions;
    private List<TurAemModel> models;
    private String deltaClassName;

    @Override
    public String toString() {
        return "TurAemContentMapping{" +
                "targetAttrDefinitions=" + targetAttrDefinitions +
                ", models=" + models +
                ", deltaClassName='" + deltaClassName +
                '}';
    }
}
