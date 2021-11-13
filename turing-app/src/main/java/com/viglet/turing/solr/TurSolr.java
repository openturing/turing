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

package com.viglet.turing.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.AbstractMap.SimpleEntry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.tika.utils.StringUtils;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.api.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.field.TurSEFieldType;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.tr.TurSNTargetingRuleMethod;
import com.viglet.turing.sn.tr.TurSNTargetingRules;
import com.viglet.turing.utils.TurSNSiteFieldUtils;

@Component
@Transactional
public class TurSolr {
	private static final Logger logger = LogManager.getLogger(TurSolr.class);

	@Autowired
	private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

	@Autowired
	private TurSolrField turSolrField;
	@Autowired
	private TurSNTargetingRules turSNTargetingRules;

	public void indexing(TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, Object> attributes) {
		logger.debug("Executing indexing ...");
		this.addDocument(turSolrInstance, turSNSite, attributes);
	}

	public void desindexing(TurSolrInstance turSolrInstance, String id) {
		logger.debug("Executing desindexing ...");

		this.deleteDocument(turSolrInstance, id);
	}

	public void desindexingByType(TurSolrInstance turSolrInstance, String type) {
		logger.debug("Executing desindexing by type {}...", type);
		this.deleteDocumentByType(turSolrInstance, type);

	}

	public void deleteDocument(TurSolrInstance turSolrInstance, String id) {
		try {
			turSolrInstance.getSolrClient().deleteById(id);
			turSolrInstance.getSolrClient().commit();
		} catch (SolrServerException | IOException e) {
			logger.error(e);
		} 
	}

	public void deleteDocumentByType(TurSolrInstance turSolrInstance, String type) {
		try {
			turSolrInstance.getSolrClient().deleteByQuery("type:" + type);
			turSolrInstance.getSolrClient().commit();
		} catch (SolrServerException | IOException e) {
			logger.error(e);
		}
	}

	// Convert to String with concatenate attributes
	private String concatenateString(@SuppressWarnings("rawtypes") List list) {
		int i = 0;
		StringBuilder sb = new StringBuilder();
		for (Object valueItem : list) {
			sb.append(turSolrField.convertFieldToString(valueItem));
			// Last Item
			if (i++ != list.size() - 1) {
				sb.append(System.getProperty("line.separator"));
			}
		}
		return sb.toString().trim();
	}

	public void addDocument(TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, Object> attributes) {
		TurSNSiteFieldUtils turSNSiteFieldUtils = new TurSNSiteFieldUtils();
		Map<String, TurSNSiteField> turSNSiteFieldMap = turSNSiteFieldUtils.toMap(turSNSite);
		SolrInputDocument document = new SolrInputDocument();

		if (attributes != null) {
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				String key = entry.getKey();
				Object attribute = entry.getValue();
				if (attribute != null) {
					if (attribute instanceof Integer) {
						int intValue = (Integer) attribute;
						document.addField(key, intValue);
					} else if (attribute instanceof org.json.JSONArray) {
						JSONArray value = (JSONArray) attribute;
						if (key.startsWith("turing_entity_") || (turSNSiteFieldMap.get(key) != null
								&& turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
							if (value != null) {
								for (int i = 0; i < value.length(); i++) {
									document.addField(key, value.getString(i));
								}
							}
						} else {
							ArrayList<String> listValues = new ArrayList<>();
							if (value != null) {
								for (int i = 0; i < value.length(); i++) {
									listValues.add(value.getString(i));
								}
							}
							document.addField(key, concatenateString(listValues));
						}
					} else if (attribute instanceof ArrayList) {
						@SuppressWarnings("rawtypes")
						ArrayList values = (ArrayList) attribute;
						if (values != null) {
							if (key.startsWith("turing_entity_") || (turSNSiteFieldMap.get(key) != null
									&& turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
								for (Object valueItem : values) {
									document.addField(key, turSolrField.convertFieldToString(valueItem));
								}
							} else {
								document.addField(key, concatenateString(values));
							}
						}
					} else {
						String valueStr = turSolrField.convertFieldToString(attribute);
						document.addField(key, valueStr);
					}
				}
			}

			addSolrDocument(turSolrInstance, document);
		}
	}

	public void addDocumentWithText(TurSolrInstance turSolrInstance, String currText) {
		SolrInputDocument document = new SolrInputDocument();
		document.addField("id", UUID.randomUUID());
		document.addField("text", currText);

		addSolrDocument(turSolrInstance, document);
	}

	private void addSolrDocument(TurSolrInstance turSolrInstance, SolrInputDocument document) {
		try {
			turSolrInstance.getSolrClient().add(document);
		} catch (SolrServerException | IOException e) {
			logger.error(e);
		}
	}

	public SpellCheckResponse autoComplete(TurSolrInstance turSolrInstance, String term) {
		SolrQuery query = new SolrQuery();
		query.setRequestHandler("/tur_suggest");
		query.setQuery(term);
		QueryResponse queryResponse;
		try {
			queryResponse = turSolrInstance.getSolrClient().query(query);
			return queryResponse.getSpellCheckResponse();
		} catch (IOException | SolrServerException e) {
			logger.error(e);
		}
		return null;
	}

	public TurSESpellCheckResult spellCheckTerm(TurSolrInstance turSolrInstance, String term) {
		SolrQuery query = new SolrQuery();
		query.setRequestHandler("/tur_spell");
		query.setQuery(term.replace("\"", ""));
		QueryResponse queryResponse;
		try {
			queryResponse = turSolrInstance.getSolrClient().query(query);
			String correctedText = queryResponse.getSpellCheckResponse().getCollatedResult();
			if (!StringUtils.isEmpty(correctedText)) {
				return new TurSESpellCheckResult(true, correctedText);
			}

		} catch (IOException | SolrServerException e) {
			logger.error(e);
		}
		return new TurSESpellCheckResult();
	}

	public TurSEResult findById(TurSolrInstance turSolrInstance, TurSNSite turSNSite, String id) {

		Map<String, TurSNSiteFieldExt> fieldExtMap = getFieldExtMap(turSNSite);

		Map<String, Object> requiredFields = getRequiredFields(turSNSite);

		SolrQuery query = new SolrQuery();
		List<TurSNSiteFieldExt> turSNSiteHlFieldExts = setHLinQuery(turSNSite, query);
		query.setQuery("id: \"" + id + "\"");

		TurSEResult turSEResult = null;
		try {
			QueryResponse queryResponse = turSolrInstance.getSolrClient().query(query);
			for (SolrDocument document : queryResponse.getResults()) {
				Map<String, List<String>> hl = getHL(turSNSite, turSNSiteHlFieldExts, queryResponse, document);
				turSEResult = createTurSEResult(fieldExtMap, requiredFields, document, hl);
			}
		} catch (SolrServerException | IOException e) {
			logger.error(e);
		}

		return turSEResult;
	}

	public TurSEResults retrieveSolrFromSN(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
			TurSNSiteSearchContext context) {

		Map<String, TurSNSiteFieldExt> fieldExtMap = getFieldExtMap(turSNSite);

		Map<String, Object> requiredFields = getRequiredFields(turSNSite);

		TurSEResults turSEResults = new TurSEResults();
		if (context.getRows() <= 0) {
			context.setRows(turSNSite.getRowsPerPage());
		}
		SimpleEntry<String, String> sortEntry = null;
		if (context.getSort() != null) {
			if (context.getSort().equalsIgnoreCase("relevance")) {
				sortEntry = null;
			} else if (context.getSort().equalsIgnoreCase("newest")) {
				sortEntry = new SimpleEntry<>(turSNSite.getDefaultDateField(), "desc");
			} else if (context.getSort().equalsIgnoreCase("oldest")) {
				sortEntry = new SimpleEntry<>(turSNSite.getDefaultDateField(), "asc");
			}
		}
		SolrQuery query = new SolrQuery();
		if (TurSNUtils.isAutoCorrectionEnabled(context, turSNSite)) {
			TurSESpellCheckResult turSESpellCheckResult = spellCheckTerm(turSolrInstance, context.getQuery());
			if (TurSNUtils.hasCorrectedText(turSESpellCheckResult)) {
				query.setQuery(turSESpellCheckResult.getCorrectedText());
			} else {
				query.setQuery(context.getQuery());
			}
		} else {

			query.setQuery(context.getQuery());
		}

		if (sortEntry != null) {
			query.setSort(sortEntry.getKey(), sortEntry.getValue().equals("asc") ? ORDER.asc : ORDER.desc);
		}

		query.setRows(context.getRows());
		query.setStart((context.getCurrentPage() * context.getRows()) - context.getRows());

		// Facet
		List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);

		if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && !turSNSiteFacetFieldExts.isEmpty()) {
			query.setFacet(true);
			query.setFacetLimit(turSNSite.getItemsPerFacet());
			query.setFacetMinCount(1);
			query.setFacetSort("count");

			for (TurSNSiteFieldExt turSNSiteFacetFieldExt : turSNSiteFacetFieldExts) {
				TurSNFieldType snType = turSNSiteFacetFieldExt.getSnType();
				if (snType == TurSNFieldType.NER || snType == TurSNFieldType.THESAURUS) {
					query.addFacetField(String.format("turing_entity_%s", turSNSiteFacetFieldExt.getName()));
				} else {
					query.addFacetField(turSNSiteFacetFieldExt.getName());
				}

			}

		}

		List<TurSNSiteFieldExt> turSNSiteHlFieldExts = setHLinQuery(turSNSite, query);

		// MLT
		List<TurSNSiteFieldExt> turSNSiteMLTFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndMltAndEnabled(turSNSite, 1, 1);
		if (turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts != null && !turSNSiteMLTFieldExts.isEmpty()) {

			StringBuilder mltFields = new StringBuilder();
			for (TurSNSiteFieldExt turSNSiteMltFieldExt : turSNSiteMLTFieldExts) {
				if (mltFields.length() != 0) {
					mltFields.append(",");
				}
				mltFields.append(turSNSiteMltFieldExt.getName());
			}

			query.set(MoreLikeThisParams.MLT, true);
			query.set(MoreLikeThisParams.MATCH_INCLUDE, true);
			query.set(MoreLikeThisParams.MIN_DOC_FREQ, 1);
			query.set(MoreLikeThisParams.MIN_TERM_FREQ, 1);
			query.set(MoreLikeThisParams.MIN_WORD_LEN, 7);
			query.set(MoreLikeThisParams.BOOST, false);
			query.set(MoreLikeThisParams.MAX_QUERY_TERMS, 1000);
			query.set(MoreLikeThisParams.SIMILARITY_FIELDS, mltFields.toString());
		}

		// Filter Query
		if (context.getFilterQueries() != null && !context.getFilterQueries().isEmpty()) {
			String[] filterQueryArr = new String[context.getFilterQueries().size()];
			filterQueryArr = context.getFilterQueries().toArray(filterQueryArr);
			query.setFilterQueries(filterQueryArr);
		}

		// Targeting Rule
		if (context.getTargetingRules() != null && !context.getTargetingRules().isEmpty())
			query.addFilterQuery(turSNTargetingRules.run(TurSNTargetingRuleMethod.AND, context.getTargetingRules()));

		QueryResponse queryResponse;
		try {
			queryResponse = turSolrInstance.getSolrClient().query(query);

			turSEResults.setNumFound(queryResponse.getResults().getNumFound());
			turSEResults.setElapsedTime(queryResponse.getElapsedTime());
			turSEResults.setqTime(queryResponse.getQTime());
			turSEResults.setStart(queryResponse.getResults().getStart());
			turSEResults.setQueryString(query.getQuery());
			turSEResults.setSort(context.getSort());
			turSEResults.setLimit(context.getRows());

			int pageCount = (int) Math.ceil(turSEResults.getNumFound() / (double) turSEResults.getLimit());
			turSEResults.setPageCount(pageCount);
			turSEResults.setCurrentPage(context.getCurrentPage());

			List<TurSEResult> results = new ArrayList<>();

			// Facet
			if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && !turSNSiteFacetFieldExts.isEmpty()) {
				List<TurSEFacetResult> facetResults = new ArrayList<>();
				for (FacetField facet : queryResponse.getFacetFields()) {

					TurSEFacetResult turSEFacetResult = new TurSEFacetResult();
					turSEFacetResult.setFacet(facet.getName());
					for (Count item : facet.getValues()) {
						turSEFacetResult.add(item.getName(),
								new TurSEFacetResultAttr(item.getName(), (int) item.getCount()));
					}
					facetResults.add(turSEFacetResult);
				}

				turSEResults.setFacetResults(facetResults);
			}

			List<TurSESimilarResult> similarResults = new ArrayList<>();
			@SuppressWarnings("rawtypes")
			SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get("moreLikeThis");

			for (SolrDocument document : queryResponse.getResults()) {
				Map<String, List<String>> hl = getHL(turSNSite, turSNSiteHlFieldExts, queryResponse, document);
				// MLT
				if (turSNSite.getMlt() == 1 && !turSNSiteMLTFieldExts.isEmpty()) {
					SolrDocumentList mltDocumentList = (SolrDocumentList) mltResp.get((String) document.get("id"));
					for (SolrDocument mltDocument : mltDocumentList) {
						TurSESimilarResult turSESimilarResult = new TurSESimilarResult();
						turSESimilarResult.setId(turSolrField.convertFieldToString(mltDocument.getFieldValue("id")));
						turSESimilarResult
								.setTitle(turSolrField.convertFieldToString(mltDocument.getFieldValue("title")));
						turSESimilarResult
								.setType(turSolrField.convertFieldToString(mltDocument.getFieldValue("type")));
						turSESimilarResult.setUrl(turSolrField.convertFieldToString(mltDocument.getFieldValue("url")));
						similarResults.add(turSESimilarResult);
					}
				}
				TurSEResult turSEResult = createTurSEResult(fieldExtMap, requiredFields, document, hl);
				results.add(turSEResult);
			}

			if (turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts != null && !turSNSiteMLTFieldExts.isEmpty()) {
				turSEResults.setSimilarResults(similarResults);
			}

			// Spell Check
			turSEResults.setSpellCheck(spellCheckTerm(turSolrInstance, context.getQuery()));

			turSEResults.setResults(results);

			return turSEResults;
		} catch (IOException | SolrServerException e) {
			logger.error(e);
		}
		return null;
	}

	public TurSEResults retrieveSolr(TurSolrInstance turSolrInstance, String txtQuery, List<String> fq, List<String> tr,
			int currentPage, String sort, int rows, String defaultSortField) {

		TurSEResults turSEResults = new TurSEResults();
		SimpleEntry<String, String> sortEntry = null;
		if (sort != null) {
			if (sort.equalsIgnoreCase("newest")) {
				sortEntry = new SimpleEntry<>(defaultSortField, "desc");
			} else if (sort.equalsIgnoreCase("oldest")) {
				sortEntry = new SimpleEntry<>(defaultSortField, "asc");
			}
		}
		SolrQuery query = new SolrQuery();

		query.setQuery(txtQuery);

		if (sortEntry != null) {
			query.setSort(sortEntry.getKey(), sortEntry.getValue().equals("asc") ? ORDER.asc : ORDER.desc);
		}

		query.setRows(rows);
		query.setStart((currentPage * rows) - rows);

		// Filter Query
		String[] filterQueryArr = new String[fq.size()];
		filterQueryArr = fq.toArray(filterQueryArr);
		query.setFilterQueries(filterQueryArr);

		// Targeting Rule
		if (tr != null && !tr.isEmpty())
			query.addFilterQuery(turSNTargetingRules.run(TurSNTargetingRuleMethod.AND, tr));

		QueryResponse queryResponse;
		try {
			queryResponse = turSolrInstance.getSolrClient().query(query);

			turSEResults.setNumFound(queryResponse.getResults().getNumFound());
			turSEResults.setElapsedTime(queryResponse.getElapsedTime());
			turSEResults.setqTime(queryResponse.getQTime());
			turSEResults.setStart(queryResponse.getResults().getStart());
			turSEResults.setQueryString(query.getQuery());
			turSEResults.setSort(sort);
			turSEResults.setLimit(rows);

			int pageCount = (int) Math.ceil(turSEResults.getNumFound() / (double) turSEResults.getLimit());
			turSEResults.setPageCount(pageCount);
			turSEResults.setCurrentPage(currentPage);

			List<TurSEResult> results = new ArrayList<>();

			for (SolrDocument document : queryResponse.getResults()) {
				TurSEResult turSEResult = createTurSEResult(document);
				results.add(turSEResult);
			}

			// Spell Check
			turSEResults.setSpellCheck(spellCheckTerm(turSolrInstance, txtQuery));

			turSEResults.setResults(results);

			return turSEResults;
		} catch (IOException | SolrServerException e) {
			logger.error(e);
		}
		return null;
	}

	private List<TurSNSiteFieldExt> setHLinQuery(TurSNSite turSNSite, SolrQuery query) {
		// Highlighting
		List<TurSNSiteFieldExt> turSNSiteHlFieldExts = getHLFields(turSNSite);

		if (turSNSite.getHl() == 1 && turSNSiteHlFieldExts != null && !turSNSiteHlFieldExts.isEmpty()) {

			StringBuilder hlFields = new StringBuilder();
			for (TurSNSiteFieldExt turSNSiteHlFieldExt : turSNSiteHlFieldExts) {
				if (hlFields.length() != 0) {
					hlFields.append(",");
				}
				hlFields.append(turSNSiteHlFieldExt.getName());
			}

			query.setHighlight(true).setHighlightSnippets(1);
			query.setParam("hl.fl", hlFields.toString());
			query.setParam("hl.fragsize", "0");
			query.setParam("hl.simple.pre", turSNSite.getHlPre());
			query.setParam("hl.simple.post", turSNSite.getHlPost());

		}
		return turSNSiteHlFieldExts;
	}

	private List<TurSNSiteFieldExt> getHLFields(TurSNSite turSNSite) {
		return turSNSiteFieldExtRepository
				.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1);
	}

	private Map<String, List<String>> getHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExts,
			QueryResponse queryResponse, SolrDocument document) {
		// HL
		Map<String, List<String>> hl = null;
		if (turSNSite.getHl() == 1 && !turSNSiteHlFieldExts.isEmpty()) {
			hl = queryResponse.getHighlighting().get(document.get("id"));
		}
		return hl;
	}

	private Map<String, TurSNSiteFieldExt> getFieldExtMap(TurSNSite turSNSite) {
		Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<>();

		List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
				1);

		for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldExts) {
			fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt);
		}
		return fieldExtMap;
	}

	private Map<String, Object> getRequiredFields(TurSNSite turSNSite) {
		Map<String, Object> requiredFields = new HashMap<>();

		List<TurSNSiteFieldExt> turSNSiteRequiredFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndRequiredAndEnabled(turSNSite, 1, 1);

		for (TurSNSiteFieldExt turSNSiteRequiredFieldExt : turSNSiteRequiredFieldExts) {
			requiredFields.put(turSNSiteRequiredFieldExt.getName(), turSNSiteRequiredFieldExt.getDefaultValue());
		}
		return requiredFields;
	}

	@SuppressWarnings("unchecked")
	private TurSEResult createTurSEResult(Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, Object> requiredFields, SolrDocument document, Map<String, List<String>> hl) {
		TurSEResult turSEResult = new TurSEResult();

		for (Object requiredFieldObject : requiredFields.keySet().toArray()) {
			String requiredField = (String) requiredFieldObject;
			if (!document.containsKey(requiredField)) {
				document.addField(requiredField, requiredFields.get(requiredField));
			}
		}
		Map<String, Object> fields = new HashMap<>();
		for (String attribute : document.getFieldNames()) {
			Object attrValue = document.getFieldValue(attribute);

			if (fieldExtMap.containsKey(attribute)) {
				TurSNSiteFieldExt turSNSiteFieldExt = fieldExtMap.get(attribute);

				if (turSNSiteFieldExt.getType() == TurSEFieldType.STRING && hl != null && hl.containsKey(attribute))
					attrValue = hl.get(attribute).get(0);

			}

			if (attribute != null && fields.containsKey(attribute)) {
				if (!(fields.get(attribute) instanceof List)) {
					List<Object> attributeValues = new ArrayList<>();
					attributeValues.add(fields.get(attribute));
					attributeValues.add(attrValue);
					fields.put(attribute, attributeValues);
				} else {
					((List<Object>) fields.get(attribute)).add(attrValue);
				}
			} else {
				fields.put(attribute, attrValue);
			}

			turSEResult.setFields(fields);
		}
		return turSEResult;
	}

	private TurSEResult createTurSEResult(SolrDocument document) {
		TurSEResult turSEResult = new TurSEResult();

		Map<String, Object> fields = new HashMap<>();
		for (String attribute : document.getFieldNames()) {
			Object attrValue = document.getFieldValue(attribute);
			fields.put(attribute, attrValue);

			turSEResult.setFields(fields);
		}
		return turSEResult;
	}
}
