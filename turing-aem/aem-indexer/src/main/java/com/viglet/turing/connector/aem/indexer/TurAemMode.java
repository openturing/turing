package com.viglet.turing.connector.aem.indexer;

import lombok.Getter;

@Getter
public enum TurAemMode {
    JCR("jcr"),
    JSON("json"),
    GRAPHQL("graphql");

    private final String name;

    TurAemMode(String name) {
        this.name = name;
    }

    public static TurAemMode getModeByName(String name) {
        for (TurAemMode mode : TurAemMode.values()) {
            if (mode.getName().equals(name)) {
                return mode;
            }
        }

        throw new IllegalArgumentException("Mode not found: " + name);
    }
}
