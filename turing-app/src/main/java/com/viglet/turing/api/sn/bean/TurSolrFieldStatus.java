package com.viglet.turing.api.sn.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

import java.util.List;

@Getter
@Setter
@Builder
public class TurSolrFieldStatus {
    private String id;
    private String externalId;
    private String name;
    private boolean facetIsCorrect;
    private List<TurSolrFieldCore> cores;
    private boolean correct;
    @Tolerate
    public TurSolrFieldStatus() {
        super();
    }
}
