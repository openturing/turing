package com.viglet.turing.connector.webcrawler;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Locale;

@Builder
@Getter
@Setter
public class TurWCCustomDocument {
    private Locale locale;
    private String localeClass;
    private List<TurWCCustomClass> attributes;
}
