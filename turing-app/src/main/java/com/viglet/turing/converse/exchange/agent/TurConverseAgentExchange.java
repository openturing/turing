/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.converse.exchange.agent;

import lombok.Getter;

import java.util.List;

@Getter
public class TurConverseAgentExchange {

    private String description;

    private String language;

    private String shortDescription;

    private String examples;

    private String linkToDocs;

    private boolean disableInteractionLogs;

    private boolean disableStackdriverLogs;

    private TurConverseAgentGAExchange googleAssistant;

    private String defaultTimezone;

    private TurConverseAgentWebhookExchange webhook;

    private boolean isPrivate;

    private String customClassifierMode;

    private float mlMinConfidence;

    private List<String> supportedLanguages;

    private String onePlatformApiVersion;

    private boolean analyzeQueryTextSentiment;

    private List<String> enabledKnowledgeBaseNames;

    private float knowledgeServiceConfidenceAdjustment;

    private boolean dialogBuilderMode;

    private String baseActionPackagesUrl;

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public void setExamples(String examples) {
        this.examples = examples;
    }

    public void setLinkToDocs(String linkToDocs) {
        this.linkToDocs = linkToDocs;
    }

    public void setDisableInteractionLogs(boolean disableInteractionLogs) {
        this.disableInteractionLogs = disableInteractionLogs;
    }

    public void setDisableStackdriverLogs(boolean disableStackdriverLogs) {
        this.disableStackdriverLogs = disableStackdriverLogs;
    }

    public void setGoogleAssistant(TurConverseAgentGAExchange googleAssistant) {
        this.googleAssistant = googleAssistant;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
    }

    public void setWebhook(TurConverseAgentWebhookExchange webhook) {
        this.webhook = webhook;
    }

    public boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public void setCustomClassifierMode(String customClassifierMode) {
        this.customClassifierMode = customClassifierMode;
    }

    public void setMlMinConfidence(float mlMinConfidence) {
        this.mlMinConfidence = mlMinConfidence;
    }

    public void setSupportedLanguages(List<String> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public void setOnePlatformApiVersion(String onePlatformApiVersion) {
        this.onePlatformApiVersion = onePlatformApiVersion;
    }

    public void setAnalyzeQueryTextSentiment(boolean analyzeQueryTextSentiment) {
        this.analyzeQueryTextSentiment = analyzeQueryTextSentiment;
    }

    public void setEnabledKnowledgeBaseNames(List<String> enabledKnowledgeBaseNames) {
        this.enabledKnowledgeBaseNames = enabledKnowledgeBaseNames;
    }

    public void setDialogBuilderMode(boolean dialogBuilderMode) {
        this.dialogBuilderMode = dialogBuilderMode;
    }

    public void setKnowledgeServiceConfidenceAdjustment(float knowledgeServiceConfidenceAdjustment) {
        this.knowledgeServiceConfidenceAdjustment = knowledgeServiceConfidenceAdjustment;
    }

    public void setBaseActionPackagesUrl(String baseActionPackagesUrl) {
        this.baseActionPackagesUrl = baseActionPackagesUrl;
    }


}
