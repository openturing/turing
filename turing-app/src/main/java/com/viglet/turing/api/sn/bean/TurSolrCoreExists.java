package com.viglet.turing.api.sn.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
public class TurSolrCoreExists {
    private String name;
    private boolean exists;

    @Tolerate
    public TurSolrCoreExists() {
        super();
    }
}
