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

package com.viglet.turing.converse;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntityTerm;
import com.viglet.turing.persistence.model.converse.intent.*;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.intent.*;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;

@Component
public class TurConverseSE {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final String FQ_KEY_VALUE = "%s:%s";
	private static final String FQ_KEY_VALUE_EXACT = "%s:\"%s\"";
	private static final String SOLR_ANY = "[\"\" TO *]";
	private static final String SOLR_FULL_QUERY = "*:*";
	private final TurConversePhraseRepository turConversePhraseRepository;
	private final TurConverseResponseRepository turConverseResponseRepository;
	private final TurConverseContextRepository turConverseContextRepository;
	private final TurConverseParameterRepository turConverseParameterRepository;
	private final TurConversePromptRepository turConversePromptRepository;
	private final TurConverseEntityRepository turConverseEntityRepository;

	private final TurSolrInstanceProcess turSolrInstanceProcess;

	@Inject
	public TurConverseSE(TurConversePhraseRepository turConversePhraseRepository,
						 TurConverseResponseRepository turConverseResponseRepository,
						 TurConverseContextRepository turConverseContextRepository,
						 TurConverseParameterRepository turConverseParameterRepository,
						 TurConversePromptRepository turConversePromptRepository,
						 TurConverseEntityRepository turConverseEntityRepository,
						 TurSolrInstanceProcess turSolrInstanceProcess) {
		this.turConversePhraseRepository = turConversePhraseRepository;
		this.turConverseResponseRepository = turConverseResponseRepository;
		this.turConverseContextRepository = turConverseContextRepository;
		this.turConverseParameterRepository = turConverseParameterRepository;
		this.turConversePromptRepository = turConversePromptRepository;
		this.turConverseEntityRepository = turConverseEntityRepository;
		this.turSolrInstanceProcess = turSolrInstanceProcess;
	}

	private Optional<SolrClient> getSolrClient(TurConverseAgent turConverseAgent) {
		TurSEInstance turSEInstance = turConverseAgent.getTurSEInstance();
		String core = turConverseAgent.getCore();
		return turSolrInstanceProcess.initSolrInstance(turSEInstance, core).map(TurSolrInstance::getSolrClient);
	}

	private String keyValueQuery(String key, String value, boolean exact) {
		return String.format(exact ? FQ_KEY_VALUE_EXACT : FQ_KEY_VALUE, key, value);
	}

	public SolrDocumentList askPhrase(TurConverseAgent turConverseAgent, String q, String nextContext) {
		return turSolrAskPhrase(turConverseAgent, q, nextContext, TurConverseConstants.INTENT_TYPE);
	}

	public SolrDocumentList askPhraseFallback(TurConverseAgent turConverseAgent, String q, String nextContext) {
		return turSolrAskPhrase(turConverseAgent, q, nextContext, TurConverseConstants.FALLBACK_INTENT_TYPE);
	}

	private SolrDocumentList turSolrAskPhrase(TurConverseAgent turConverseAgent, String q, String nextContext,
			String type) {
		SolrQuery query = new SolrQuery();

		if (nextContext != null) {
			query.addFilterQuery(keyValueQuery(TurConverseConstants.CONTEXT_INTPUT, nextContext, true));
		} else {
			query.addFilterQuery(keyValueQuery("-".concat(TurConverseConstants.CONTEXT_INTPUT), SOLR_ANY, true));
		}
		query.addFilterQuery(keyValueQuery(TurConverseConstants.TYPE, type, true));
		query.addFilterQuery(keyValueQuery(TurConverseConstants.AGENT, turConverseAgent.getId(), true));
		query.setQuery(keyValueQuery(TurConverseConstants.PHRASES, q, true));

		return executeSolrQuery(turConverseAgent, query);
	}

	private SolrDocumentList executeSolrQuery(TurConverseAgent turConverseAgent, SolrQuery queryParameter) {
		try {
			Optional<SolrClient> solrClient = this.getSolrClient(turConverseAgent);
			if (solrClient.isPresent()) {
				QueryResponse queryResponseParameter = solrClient.get().query(queryParameter);
				return queryResponseParameter.getResults();
			}
		} catch (SolrServerException | IOException e) {
			logger.error(e.getMessage(), e);
		}
		return new SolrDocumentList();
	}

	SolrDocumentList solrGetActionAndParameters(TurConverseAgent turConverseAgent, String intent) {
		SolrQuery queryParameter = new SolrQuery();
		queryParameter
				.addFilterQuery(keyValueQuery(TurConverseConstants.TYPE, TurConverseConstants.PARAMETER_TYPE, true));
		queryParameter.addFilterQuery(keyValueQuery(TurConverseConstants.AGENT, turConverseAgent.getId(), true));
		queryParameter.addFilterQuery(keyValueQuery(TurConverseConstants.INTENT, intent, true));
		queryParameter.addFilterQuery(keyValueQuery(TurConverseConstants.PROMPTS, SOLR_ANY, true));
		queryParameter.setQuery(SOLR_FULL_QUERY);
		return executeSolrQuery(turConverseAgent, queryParameter);
	}

	SolrDocumentList solrGetIntent(TurConverseAgent turConverseAgent, String intent) {
		SolrQuery query = new SolrQuery();
		query.addFilterQuery(keyValueQuery(TurConverseConstants.TYPE, TurConverseConstants.INTENT_TYPE, true));
		query.addFilterQuery(keyValueQuery(TurConverseConstants.AGENT, turConverseAgent.getId(), true));
		query.addFilterQuery(keyValueQuery(TurConverseConstants.ID, intent, true));
		query.setQuery(SOLR_FULL_QUERY);
		return executeSolrQuery(turConverseAgent, query);

	}

	SolrDocumentList solrGetFallbackIntent(TurConverseAgent turConverseAgent) {
		SolrQuery query = new SolrQuery();
		query.addFilterQuery(keyValueQuery(TurConverseConstants.TYPE, TurConverseConstants.FALLBACK_INTENT_TYPE, true));
		query.addFilterQuery(keyValueQuery(TurConverseConstants.AGENT, turConverseAgent.getId(), true));
		query.setQuery(SOLR_FULL_QUERY);
		return executeSolrQuery(turConverseAgent, query);

	}

	public SolrDocumentList sorlGetParameterValue(String text, TurConverseChat turConverseChat, String intentId) {
		logger.debug("sorlGetParameterValue");

		SolrQuery query = new SolrQuery();
		query.setParam("defType", "edismax");
		query.setParam("pf", "phrase^100");
		query.addFilterQuery(keyValueQuery(TurConverseConstants.TYPE, TurConverseConstants.TERM_TYPE, true));
		query.addFilterQuery(keyValueQuery(TurConverseConstants.AGENT, turConverseChat.getAgent().getId(), true));
		query.addFilterQuery(keyValueQuery(TurConverseConstants.INTENT, intentId, true));
		query.setQuery(keyValueQuery(TurConverseConstants.PHRASE, text, false));
		return executeSolrQuery(turConverseChat.getAgent(), query);
	}

	public void desindexAll(TurConverseAgent turConverseAgent) {

		Optional<SolrClient> solrClient = this.getSolrClient(turConverseAgent);
		if (solrClient.isPresent()) {
			try {
				solrClient.get()
						.deleteByQuery(keyValueQuery(TurConverseConstants.AGENT, turConverseAgent.getId(), true));
				solrClient.get().commit();
			} catch (SolrServerException | IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	public void index(TurConverseIntent turConverseIntent) {
		turConverseIntent
				.setContextInputs(turConverseContextRepository.findByIntentInputs_Id(turConverseIntent.getId()));
		turConverseIntent
				.setContextOutputs(turConverseContextRepository.findByIntentOutputs_Id(turConverseIntent.getId()));
		turConverseIntent.setPhrases(turConversePhraseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setResponses(turConverseResponseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setParameters(turConverseParameterRepository.findByIntent(turConverseIntent));

		for (TurConverseParameter parameter : turConverseIntent.getParameters()) {
			parameter.setPrompts(turConversePromptRepository.findByParameter(parameter));
		}
		turSolrInstanceProcess.initSolrInstance(turConverseIntent.getAgent().getTurSEInstance(),
				turConverseIntent.getAgent().getCore()).ifPresent(solrInstance -> {
					this.indexIntent(turConverseIntent, solrInstance.getSolrClient());
					this.indexParameters(turConverseIntent, solrInstance.getSolrClient());
				});
	}

	private void indexParameters(TurConverseIntent turConverseIntent, SolrClient solrClient) {
		SolrInputDocument document;
		for (TurConverseParameter parameter : turConverseIntent.getParameters()) {
			document = new SolrInputDocument();
			document.addField(TurConverseConstants.ID, parameter.getId());
			document.addField(TurConverseConstants.AGENT, turConverseIntent.getAgent().getId());
			document.addField(TurConverseConstants.INTENT, turConverseIntent.getId());
			document.addField(TurConverseConstants.TYPE, TurConverseConstants.PARAMETER_TYPE);
			document.addField(TurConverseConstants.ACTION, turConverseIntent.getActionName());
			document.addField(TurConverseConstants.NAME, parameter.getName());
			document.addField(TurConverseConstants.POSITION, parameter.getPosition());

			List<String> promptList = new ArrayList<>();
			for (TurConversePrompt prompt : parameter.getPrompts()) {
				promptList.add(prompt.getText());
			}
			document.addField(TurConverseConstants.PROMPTS, promptList);

			this.indexToSolr(solrClient, document);
		}
	}

	private void indexToSolr(SolrClient solrClient, SolrInputDocument document) {
		try {
			solrClient.add(document);
			solrClient.commit();
		} catch (SolrServerException | IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void indexIntent(TurConverseIntent turConverseIntent, SolrClient solrClient) {
		SolrInputDocument document = new SolrInputDocument();

		document.addField(TurConverseConstants.ID, turConverseIntent.getId());
		document.addField(TurConverseConstants.AGENT, turConverseIntent.getAgent().getId());
		document.addField(TurConverseConstants.TYPE,
				turConverseIntent.isFallback() ? TurConverseConstants.FALLBACK_INTENT_TYPE
						: TurConverseConstants.INTENT_TYPE);

		document.addField(TurConverseConstants.NAME, turConverseIntent.getName());
		for (TurConverseContext contextInput : turConverseIntent.getContextInputs())
			document.addField(TurConverseConstants.CONTEXT_INTPUT, contextInput.getText());

		for (TurConverseContext contextOutput : turConverseIntent.getContextOutputs())
			document.addField(TurConverseConstants.CONTEXT_OUTPUT, contextOutput.getText());

		Set<TurConverseEntity> entities = turConverseEntityRepository.findByAgent(turConverseIntent.getAgent());

		for (TurConversePhrase phrase : turConverseIntent.getPhrases()) {

			if (phrase.getText().contains("@")) {
				this.indexTerms(turConverseIntent, solrClient, document, entities, phrase);

			} else {
				document.addField(TurConverseConstants.PHRASES, phrase.getText());
			}

		}

		for (TurConverseResponse response : turConverseIntent.getResponses())
			document.addField(TurConverseConstants.RESPONSES, response.getText());

		document.addField(TurConverseConstants.HAS_PARAMETERS, !turConverseIntent.getParameters().isEmpty());

		this.indexToSolr(solrClient, document);
	}

	private String entityName(String name) {
		return String.format("@%s", name);
	}

	private void indexTerms(TurConverseIntent turConverseIntent, SolrClient solrClient, SolrInputDocument document,
			Set<TurConverseEntity> entities, TurConversePhrase phrase) {
		for (TurConverseEntity entity : entities) {
			indextTermsFromEntity(turConverseIntent, solrClient, document, phrase, entity);
		}
	}

	private void indextTermsFromEntity(TurConverseIntent turConverseIntent, SolrClient solrClient,
			SolrInputDocument document, TurConversePhrase phrase, TurConverseEntity entity) {
		if (phrase.getText().contains(entityName(entity.getName()))) {
			for (TurConverseEntityTerm term : entity.getTerms()) {
				Set<TurConverseParameter> parameters = turConverseParameterRepository
						.findByIntentAndEntity(turConverseIntent, entityName(entity.getName()));
				phrasesVariationfromSynonyms(turConverseIntent, solrClient, document, phrase, entity, term, parameters);
			}
		}
	}

	private void phrasesVariationfromSynonyms(TurConverseIntent turConverseIntent, SolrClient solrClient,
			SolrInputDocument document, TurConversePhrase phrase, TurConverseEntity entity, TurConverseEntityTerm term,
			Set<TurConverseParameter> parameters) {
		for (String synonym : term.getSynonyms()) {
			String phraseFormatted = phrase.getText().replaceAll(entityName(entity.getName()), synonym);

			document.addField("phrases", phraseFormatted);

			if (!parameters.isEmpty()) {
				for (TurConverseParameter parameter : parameters) {
					indexTerm(turConverseIntent, solrClient, entity, term, synonym, phraseFormatted, parameter);
				}
			}
		}
	}

	private void indexTerm(TurConverseIntent turConverseIntent, SolrClient solrClient, TurConverseEntity entity,
			TurConverseEntityTerm term, String synonym, String phraseFormatted, TurConverseParameter parameter) {
		if (logger.isDebugEnabled())
			logger.debug("Have parameters to Entity: {}", entity.getName());
		SolrInputDocument termDocument = new SolrInputDocument();
		termDocument.addField(TurConverseConstants.ID,
				String.format("%s.%s:%s", turConverseIntent.getId(), term.getId(), synonym));
		termDocument.addField(TurConverseConstants.AGENT, turConverseIntent.getAgent().getId());
		termDocument.addField(TurConverseConstants.INTENT, turConverseIntent.getId());
		termDocument.addField(TurConverseConstants.TYPE, TurConverseConstants.TERM_TYPE);
		termDocument.addField(TurConverseConstants.PARAMETERS,
				keyValueQuery(parameter.getName(), term.getName(), false));
		termDocument.addField(TurConverseConstants.PHRASE, phraseFormatted);
		if (logger.isDebugEnabled())
			logger.debug("Term parameters {}:{}", parameter.getName(), entity.getName());
		this.indexToSolr(solrClient, termDocument);
	}

	public void desindex(TurConverseIntent turConverseIntent) {
		turSolrInstanceProcess.initSolrInstance(turConverseIntent.getAgent().getTurSEInstance(),
				turConverseIntent.getAgent().getCore()).ifPresent(solrInstance -> {
					try {
						solrInstance.getSolrClient()
								.deleteByQuery(keyValueQuery(TurConverseConstants.ID, turConverseIntent.getId(), true));
						solrInstance.getSolrClient().commit();
					} catch (SolrServerException | IOException e) {
						logger.error(e.getMessage(), e);
					}
				});

	}
}
