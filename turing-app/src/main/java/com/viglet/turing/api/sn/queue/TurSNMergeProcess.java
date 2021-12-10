/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.turing.api.sn.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;

/**
 * 
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
@Component
public class TurSNMergeProcess {
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;
	@Autowired
	private TurSolr turSolr;

	public void mergeDocuments(TurSNSite turSNSite, String locale, Map<String, Object> attributesFrom) {
		TurSNMergeProviders turSNMergeProviders = new TurSNMergeProviders();
		turSNMergeProviders.setFromProvider("Nutch");
		turSNMergeProviders.setToProvider("WEM");
		turSNMergeProviders.setRelationAttribFrom("id");
		turSNMergeProviders.setRelationAttribTo("url");
		turSNMergeProviders.setTurSNSite(turSNSite);
		turSNMergeProviders.setLocale(locale);
		List<String> overwrittenAttribs = new ArrayList<>();
		overwrittenAttribs.add("text");
		// Logic
		if (attributesFrom.containsKey("providers")) {
			@SuppressWarnings("unchecked")
			List<String> providers = (ArrayList<String>) attributesFrom.get("providers");

			if (providers.contains(turSNMergeProviders.getFromProvider())) {

				String relationValue = (String) attributesFrom.get(turSNMergeProviders.getRelationAttribFrom());
				List<SolrDocument> resultsFrom = solrDocumentsFrom(turSNMergeProviders, relationValue);
				List<SolrDocument> resultsTo = solrDocumentsTo(turSNMergeProviders, relationValue);

				if (hasSolrDocuments(resultsFrom) && hasSolrDocuments(resultsTo)) {
					desindexSolrDocuments(turSNMergeProviders, resultsFrom);
				}

				if (hasSolrDocuments(resultsTo)) {
					TurSEResult turSEResult = turSolr.createTurSEResult(resultsTo.iterator().next());
					doMergeContent(attributesFrom, turSEResult.getFields(), turSNMergeProviders);
				}
			}
		}
	}

	private boolean hasSolrDocuments(List<SolrDocument> resultsTo) {
		return resultsTo != null && !resultsTo.isEmpty();
	}

	private SolrDocumentList solrDocumentsTo(TurSNMergeProviders turSNMergeProviders, String relationValue) {
		Map<String, Object> queryMapTo = new HashMap<>();
		queryMapTo.put(turSNMergeProviders.getRelationAttribTo(), relationValue);
		queryMapTo.put("providers", turSNMergeProviders.getToProvider());
		SolrDocumentList resultsTo = solrResultAnd(turSNMergeProviders, queryMapTo);
		return resultsTo;
	}

	private SolrDocumentList solrDocumentsFrom(TurSNMergeProviders turSNMergeProviders, String relationValue) {
		Map<String, Object> queryMapFrom = new HashMap<>();
		queryMapFrom.put(turSNMergeProviders.getRelationAttribFrom(), relationValue);
		queryMapFrom.put("providers", turSNMergeProviders.getFromProvider());
		SolrDocumentList resultsFrom = solrResultAnd(turSNMergeProviders, queryMapFrom);
		return resultsFrom;
	}

	private void desindexSolrDocuments(TurSNMergeProviders turSNMergeProviders, List<SolrDocument> results) {

		turSolrInstanceProcess.initSolrInstance(turSNMergeProviders.getTurSNSite(), turSNMergeProviders.getLocale())
				.ifPresent(turSolrInstance -> results
						.forEach(result -> turSolr.desindexing(turSolrInstance, result.get("id").toString())));
	}

	private SolrDocumentList solrResultAnd(TurSNMergeProviders turSNMergeProviders, Map<String, Object> attributes) {
		return turSolrInstanceProcess
				.initSolrInstance(turSNMergeProviders.getTurSNSite(), turSNMergeProviders.getLocale())
				.map(turSolrInstance -> {
					return turSolr.solrResultAnd(turSolrInstance, attributes);
				}).orElse(new SolrDocumentList());

	}

	private void doMergeContent(Map<String, Object> attributesFrom, Map<String, Object> attributesTo,
			TurSNMergeProviders turSNMergeProviders) {
		addProviderToSolrDocument(attributesTo, turSNMergeProviders);
		addOverwrittenAttributesToSolrDocument(attributesFrom, attributesTo, turSNMergeProviders);
	}

	private void addOverwrittenAttributesToSolrDocument(Map<String, Object> attributesFrom,
			Map<String, Object> attributesTo, TurSNMergeProviders turSNMergeProviders) {
		attributesFrom.entrySet().forEach(attributeFrom -> {
			if (turSNMergeProviders.getOverwrittenFields().contains(attributeFrom.getKey())) {
				if (!attributesTo.containsKey(attributeFrom.getKey())) {
					attributesTo.put(attributeFrom.getKey(), attributeFrom.getValue());
				}
			}
		});
	}

	private void addProviderToSolrDocument(Map<String, Object> attributesTo, TurSNMergeProviders turSNMergeProviders) {
		@SuppressWarnings("unchecked")
		List<String> providers = (ArrayList<String>) attributesTo.get("providers");
		if (!providers.contains(turSNMergeProviders.getFromProvider())) {
			providers.add(turSNMergeProviders.getFromProvider());
			attributesTo.put("providers", providers);
		}
	}
}
