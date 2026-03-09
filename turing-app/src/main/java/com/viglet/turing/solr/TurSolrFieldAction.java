package com.viglet.turing.solr;

public enum TurSolrFieldAction {
    ADD("add-field"),
    ADD_COPY("add-copy-field"),
    REPLACE("replace-field"),
    DELETE("delete-field"),
    DELETE_COPY("delete-copy-field");

    private final String action;

    TurSolrFieldAction(String action) {
        this.action = action;
    }

    public String getSolrAction() {
        return action;
    }
}
