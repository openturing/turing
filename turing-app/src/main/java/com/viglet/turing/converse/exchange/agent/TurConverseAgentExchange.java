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

import java.util.List;

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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		this.shortDescription = shortDescription;
	}

	public String getExamples() {
		return examples;
	}

	public void setExamples(String examples) {
		this.examples = examples;
	}

	public String getLinkToDocs() {
		return linkToDocs;
	}

	public void setLinkToDocs(String linkToDocs) {
		this.linkToDocs = linkToDocs;
	}

	public boolean isDisableInteractionLogs() {
		return disableInteractionLogs;
	}

	public void setDisableInteractionLogs(boolean disableInteractionLogs) {
		this.disableInteractionLogs = disableInteractionLogs;
	}

	public boolean isDisableStackdriverLogs() {
		return disableStackdriverLogs;
	}

	public void setDisableStackdriverLogs(boolean disableStackdriverLogs) {
		this.disableStackdriverLogs = disableStackdriverLogs;
	}

	public TurConverseAgentGAExchange getGoogleAssistant() {
		return googleAssistant;
	}

	public void setGoogleAssistant(TurConverseAgentGAExchange googleAssistant) {
		this.googleAssistant = googleAssistant;
	}

	public String getDefaultTimezone() {
		return defaultTimezone;
	}

	public void setDefaultTimezone(String defaultTimezone) {
		this.defaultTimezone = defaultTimezone;
	}

	public TurConverseAgentWebhookExchange getWebhook() {
		return webhook;
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

	public String getCustomClassifierMode() {
		return customClassifierMode;
	}

	public void setCustomClassifierMode(String customClassifierMode) {
		this.customClassifierMode = customClassifierMode;
	}

	public float getMlMinConfidence() {
		return mlMinConfidence;
	}

	public void setMlMinConfidence(float mlMinConfidence) {
		this.mlMinConfidence = mlMinConfidence;
	}

	public List<String> getSupportedLanguages() {
		return supportedLanguages;
	}

	public void setSupportedLanguages(List<String> supportedLanguages) {
		this.supportedLanguages = supportedLanguages;
	}

	public String getOnePlatformApiVersion() {
		return onePlatformApiVersion;
	}

	public void setOnePlatformApiVersion(String onePlatformApiVersion) {
		this.onePlatformApiVersion = onePlatformApiVersion;
	}

	public boolean isAnalyzeQueryTextSentiment() {
		return analyzeQueryTextSentiment;
	}

	public void setAnalyzeQueryTextSentiment(boolean analyzeQueryTextSentiment) {
		this.analyzeQueryTextSentiment = analyzeQueryTextSentiment;
	}

	public List<String> getEnabledKnowledgeBaseNames() {
		return enabledKnowledgeBaseNames;
	}

	public void setEnabledKnowledgeBaseNames(List<String> enabledKnowledgeBaseNames) {
		this.enabledKnowledgeBaseNames = enabledKnowledgeBaseNames;
	}

	public boolean isDialogBuilderMode() {
		return dialogBuilderMode;
	}

	public void setDialogBuilderMode(boolean dialogBuilderMode) {
		this.dialogBuilderMode = dialogBuilderMode;
	}

	public float getKnowledgeServiceConfidenceAdjustment() {
		return knowledgeServiceConfidenceAdjustment;
	}

	public void setKnowledgeServiceConfidenceAdjustment(float knowledgeServiceConfidenceAdjustment) {
		this.knowledgeServiceConfidenceAdjustment = knowledgeServiceConfidenceAdjustment;
	}

	public String getBaseActionPackagesUrl() {
		return baseActionPackagesUrl;
	}

	public void setBaseActionPackagesUrl(String baseActionPackagesUrl) {
		this.baseActionPackagesUrl = baseActionPackagesUrl;
	}

	
}
