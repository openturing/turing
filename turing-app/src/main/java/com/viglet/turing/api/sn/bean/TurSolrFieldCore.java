package com.viglet.turing.api.sn.bean;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
public class TurSolrFieldCore {
    String name;
    boolean exists;
    private String type;
    private boolean typeIsCorrect;
    private boolean multiValued;
    private boolean multiValuedIsCorrect;
    private boolean correct;
    @Tolerate
    public TurSolrFieldCore() {
        super();
    }
}
