package com.viglet.turing.api.sn.job;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;
@Builder
@Data
public class TurSNJobSpecAttribute {
    private String name;
    private String description;
    private String facetName;
    private TurSEFieldType type;
    private boolean multiValued;

    @Tolerate
    TurSNJobSpecAttribute() {}
}
