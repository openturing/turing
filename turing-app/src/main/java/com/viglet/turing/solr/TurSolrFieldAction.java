package com.viglet.turing.solr;

import java.io.Serializable;

public enum TurSolrFieldAction implements Serializable {
    ADD("add-field"),
    REPLACE("replace-field");

    private final String action;

    TurSolrFieldAction(String action) {
        this.action = action;
    }

    public String getSolrAction() {
        return action;
    }
}
