package com.viglet.turing.api.sn.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

import java.util.List;
@Getter
@Setter
@Builder
public class TurSNFieldExtCheck {
    private List<TurSolrCoreExists> cores;
    private  List<TurSolrFieldStatus> fields;
    @Tolerate
    public TurSNFieldExtCheck() {
        super();
    }

}
