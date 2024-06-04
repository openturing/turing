package com.viglet.turing.solr.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
public class TurSolrFieldBean {
    private String name;
    private String type;
    private boolean multiValued;
    private boolean indexed;
    private boolean stored;

    @Tolerate
    public TurSolrFieldBean() {
        super();
    }
}

