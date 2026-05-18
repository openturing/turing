/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.exchange.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;

import lombok.Getter;
import lombok.Setter;

/**
 * Export wrapper for {@link TurCustomTool}: carries the tool metadata but
 * moves the Groovy script out of the envelope into a sibling file under
 * {@code tools/} in the ZIP. {@link #groovyScriptFile} holds the relative
 * path the importer reads back.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TurCustomToolExchange {

    private String id;
    private String title;
    private String description;
    private String descriptionMetaPrompt;
    private String icon;
    private String llmDescription;
    private String llmDescriptionMetaPrompt;
    private String groovyMetaPrompt;
    private String parametersJson;
    private String returnType;
    private int enabled;

    /** Relative path inside the ZIP to the Groovy script file. */
    private String groovyScriptFile;

    public static TurCustomToolExchange fromEntity(TurCustomTool tool, String groovyScriptFile) {
        TurCustomToolExchange e = new TurCustomToolExchange();
        e.id = tool.getId();
        e.title = tool.getTitle();
        e.description = tool.getDescription();
        e.descriptionMetaPrompt = tool.getDescriptionMetaPrompt();
        e.icon = tool.getIcon();
        e.llmDescription = tool.getLlmDescription();
        e.llmDescriptionMetaPrompt = tool.getLlmDescriptionMetaPrompt();
        e.groovyMetaPrompt = tool.getGroovyMetaPrompt();
        e.parametersJson = tool.getParametersJson();
        e.returnType = tool.getReturnType();
        e.enabled = tool.getEnabled();
        e.groovyScriptFile = groovyScriptFile;
        return e;
    }
}
