package com.viglet.turing.connector.cms.mappers;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TurCmsContentMapping {
    private List<TurSNAttributeSpec> targetAttrDefinitions;
    private List<TurCmsModel> models;
    private String deltaClassName;

    @Override
    public String toString() {
        return "TurCmsContentMapping{" +
                "targetAttrDefinitions=" + targetAttrDefinitions +
                ", models=" + models +
                ", deltaClassName='" + deltaClassName +
                '}';
    }
}
