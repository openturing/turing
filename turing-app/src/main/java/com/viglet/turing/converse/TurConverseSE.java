/*
 * Copyright (C) 2021 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.converse;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntityTerm;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseParameter;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConversePrompt;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseParameterRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePromptRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;
import com.viglet.turing.solr.TurSolrInstanceProcess;

@Component
public class TurConverseSE {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final String FQ_KEY_VALUE = "%s:%s";
	private static final String FQ_KEY_VALUE_EXACT = "%s:\"%s\"";
	private static final String SOLR_ANY = "[\"\" TO *]";
	private static final String SOLR_FULL_QUERY = "*:*";
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	private TurConverseContextRepository turConverseContextRepository;
	@Autowired
	private TurConverseParameterRepository turConverseParameterRepository;
	@Autowired
	private TurConversePromptRepository turConversePromptRepository;
	@Autowired
	private TurConverseEntityRepository turConverseEntityRepository;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;

	private Optional<SolrClient> getSolrClient(TurConverseAgent turConverseAgent) {
		TurSEInstance turSEInstance = turConverseAgent.getTurSEInstance();
		String core = turConverseAgent.getCore();
		return turSolrInstanceProcess.initSolrInstance(turSEInstance, core)
				.map(solrInstance -> Optional.of(solrInstance.getSolrClient())).orElse(Optional.empty());
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
		SolrDocumentList results = null;
		try {
			Optional<SolrClient> solrClient = this.getSolrClient(turConverseAgent);
			if (solrClient.isPresent()) {
				QueryResponse queryResponseParameter = solrClient.get().query(queryParameter);
				return queryResponseParameter.getResults();
			}
		} catch (SolrServerException | IOException e) {
			logger.error(e.getMessage(), e);
		}
		return results;
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
