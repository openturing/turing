package com.viglet.turing.api.sn.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSNFieldExtCheckError {
    private String coreName;
    private String fieldName;
    private boolean type;
    private boolean multivalued;
}
