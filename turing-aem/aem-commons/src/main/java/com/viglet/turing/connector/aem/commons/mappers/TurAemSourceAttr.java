package com.viglet.turing.connector.aem.commons.mappers;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
public class TurAemSourceAttr {
    private String name;
    private String className;
    private boolean uniqueValues;
    private boolean convertHtmlToText;

    @Tolerate
    public TurAemSourceAttr() {
        super();
    }

    @Override
    public String toString() {
        return "TurAemSourceAttr{" +
                "name='" + name + '\'' +
                ", className='" + className + '\'' +
                ", uniqueValues=" + uniqueValues +
                ", convertHtmlToText=" + convertHtmlToText +
                '}';
    }
}
