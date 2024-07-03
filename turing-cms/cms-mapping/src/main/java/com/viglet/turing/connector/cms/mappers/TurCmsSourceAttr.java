package com.viglet.turing.connector.cms.mappers;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Getter
@Setter
@Builder
public class TurCmsSourceAttr {
    private String name;
    private String className;
    private boolean uniqueValues;
    private boolean convertHtmlToText;

    @Tolerate
    public TurCmsSourceAttr() {
        super();
    }

    @Override
    public String toString() {
        return "TurCmsSourceAttr{" +
                "name='" + name + '\'' +
                ", className='" + className + '\'' +
                ", uniqueValues=" + uniqueValues +
                ", convertHtmlToText=" + convertHtmlToText +
                '}';
    }
}
