package com.viglet.turing.connector.webcrawler;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Getter
@Setter
public class TurWCCustomClass {
    private String attribute;
    private String className;
    private String text;

    public TurWCCustomClass(String attribute, String className, String text) {
        this.attribute = attribute;
        this.className = className;
        this.text = text;
    }

}
