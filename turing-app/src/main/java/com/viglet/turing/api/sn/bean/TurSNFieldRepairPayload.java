package com.viglet.turing.api.sn.bean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSNFieldRepairPayload {
    private String id;
    private String core;
    private TurSNFieldRepairType repairType;
    private String value;
}
