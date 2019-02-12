/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
import java.util.concurrent.TimeUnit;
import java.util.AbstractMap.SimpleEntry;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.SolrHttpRequestRetryHandler;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.field.TurSEFieldType;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.util.TurSNSiteFieldUtils;

@Component
@Transactional
public class TurSolr {
	private final int ADD_UNTIL_COMMIT = 50;
	private static int addUntilCommitCounter;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	static final Logger logger = LogManager.getLogger(TurSolr.class.getName());
	@Autowired
	private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	private TurSolrField turSolrField;

	private TurSEInstance currSE = null;
	private Map<String, Object> attributes = null;
	private TurSNSite turSNSite = null;
	private CloseableHttpClient httpClient = null;

	String currText = null;

	SolrClient solrClient = null;

	@PostConstruct
	public void initialize() {
		if (logger.isDebugEnabled()) {
			logger.debug("TurSolr initialized");
		}
		if (httpClient == null) {
			httpClient = createClient();
		}
	}

	@PreDestroy
	public void destroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("TurSolr destroyed");
		}
		this.close();
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	public TurSEInstance getCurrSE() {
		return currSE;
	}

	public void setCurrSE(TurSEInstance currSE) {
		this.currSE = currSE;
	}

	public String getCurrText() {
		return currText;
	}

	public void setCurrText(String currText) {
		this.currText = currText;
	}

	private static CloseableHttpClient createClient() {
		// code derived from org.apache.solr.client.solrj.impl.HttpClientUtil,
		// simplified and removed irrelevant config
		Registry<ConnectionSocketFactory> schemaRegistry = HttpClientUtil.getSchemaRegisteryProvider()
				.getSchemaRegistry();
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(schemaRegistry);
		cm.setMaxTotal(10000);
		cm.setDefaultMaxPerRoute(10000);
		cm.setValidateAfterInactivity(3000);

		RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
				// .setConnectTimeout(HttpClientUtil.DEFAULT_CONNECT_TIMEOUT)
				// .setSocketTimeout(HttpClientUtil.DEFAULT_SO_TIMEOUT);
				.setConnectTimeout(30000).setSocketTimeout(30000);

		HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().setKeepAliveStrategy((response, context) -> -1)
				.evictIdleConnections(50000, TimeUnit.MILLISECONDS)
				.setDefaultRequestConfig(requestConfigBuilder.build())
				.setRetryHandler(new SolrHttpRequestRetryHandler(0)).disableContentCompression().useSystemProperties()
				.setConnectionManager(cm);

		return httpClientBuilder.build();
	}

	public void init(TurSEInstance turSEInstance) {
		this.setCurrSE(turSEInstance);
		if (httpClient == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("TurSolr createClient");
			}
			httpClient = createClient();
		}
		if (turSEInstance != null) {
			String urlString = "http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort() + "/solr/"
					+ turSNSite.getCore();
			solrClient = new HttpSolrClient.Builder(urlString).withHttpClient(httpClient).withConnectionTimeout(30000)
					.withSocketTimeout(30000).build();

		}
	}

	public void close() {
		try {
			if (solrClient != null) {
				solrClient.close();
				solrClient = null;
			}
			if (httpClient != null) {
				httpClient.close();
				httpClient = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void init(TurSNSite turSNSite) {
		this.turSNSite = turSNSite;
		init(turSNSite.getTurSEInstance());
	}

	public void init(TurSNSite turSNSite, String text) {
		init(turSNSite);
		this.setCurrText(text);
	}

	public void init(TurSEInstance turSEInstance, String text) {

		init(turSEInstance);
		this.setCurrText(text);
	}

	public void init(TurSEInstance turSEInstance, Map<String, Object> attributes) {
		init(turSEInstance);
		this.setAttributes(attributes);
		this.setCurrText(null);
	}

	public void init(TurSNSite turSNSite, Map<String, Object> attributes) {
		init(turSNSite);
		this.setAttributes(attributes);
		this.setCurrText(null);
	}

	public void init() {
		TurSEInstance turSEInstance = turSEInstanceRepository
				.findById(Integer.parseInt(turConfigVarRepository.findById("DEFAULT_SE").get().getValue()));
		init(turSEInstance);
	}

	public void indexing() throws JSONException {
		logger.debug("Executing indexing ...");
		if (solrClient != null) {
			this.addDocument();
		}
	}

	public void desindexing(String id) throws JSONException {
		logger.debug("Executing desindexing ...");
		if (solrClient != null) {
			this.deleteDocument(id);
		}
	}

	public void desindexingByType(String type) throws JSONException {
		logger.debug("Executing desindexing by type " + type + "...");
		if (solrClient != null) {
			this.deleteDocumentByType(type);
		}
	}

	public void deleteDocument(String id) {
		try {
			@SuppressWarnings("unused")
			UpdateResponse response = solrClient.deleteById(id);
			solrClient.commit();
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void deleteDocumentByType(String type) {
		try {
			@SuppressWarnings("unused")
			UpdateResponse response = solrClient.deleteByQuery("type:" + type);
			solrClient.commit();
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// Convert to String with concatenate attributes
	private String concatenateString(List list) {
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

	public void addDocument() throws JSONException {
		TurSNSiteFieldUtils turSNSiteFieldUtils = new TurSNSiteFieldUtils();
		Map<String, TurSNSiteField> turSNSiteFieldMap = turSNSiteFieldUtils.toMap(turSNSite);
		SolrInputDocument document = new SolrInputDocument();

		// JSONObject jsonNLP = turNLPResults.getJsonResult();
		Map<String, Object> attributes = this.getAttributes();

		if (attributes != null) {

			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				String key = entry.getKey();
				Object attribute = entry.getValue();				
				if (attribute != null) {
					logger.info("key: " + key);
					logger.info("attribute: " + attribute.toString());
					logger.info("class: " + attribute.getClass().getName());
					if (turSNSiteFieldMap.get(key) != null) {
						logger.info("multivalue: " + turSNSiteFieldMap.get(key).getMultiValued());
					}
					if (attribute.getClass().getName().equals("java.lang.Integer")) {
						logger.info("is Integer");
						int intValue = (Integer) attribute;
						document.addField(key, intValue);
					} else if (attribute.getClass().getName().equals("org.json.JSONArray")) {
						logger.info("is JSONArray");
						JSONArray value = (JSONArray) attribute;
						if (key.startsWith("turing_entity_") || (turSNSiteFieldMap.get(key) != null
								&& turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
							logger.info("is MultiValued");
							if (value != null) {
								for (int i = 0; i < value.length(); i++) {
									document.addField(key, value.getString(i));
								}
							}
						} else {
							logger.info("is not MultiValued");
							ArrayList<String> listValues = new ArrayList<String>();
							if (value != null) {
								for (int i = 0; i < value.length(); i++) {
									listValues.add(value.getString(i));
								}
							}
							document.addField(key, concatenateString(listValues));
						}
					} else if (attribute instanceof ArrayList) {
						logger.info("is ArrayList");
						ArrayList values = (ArrayList) attribute;
						if (values != null) {
							if (key.startsWith("turing_entity_") || (turSNSiteFieldMap.get(key) != null
									&& turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
								logger.info("is MultiValued");
								for (Object valueItem : values) {
									document.addField(key, turSolrField.convertFieldToString(valueItem));
								}
							} else {
								logger.info("is not MultiValued");
								document.addField(key, concatenateString(values));
							}
						}
					} else {
						logger.info("is undefined");
						String valueStr = turSolrField.convertFieldToString(attribute);
						document.addField(key, valueStr);
					}
				}
			}

		} else {
			UUID documentId = UUID.randomUUID();
			document.addField("id", documentId);
			document.addField("text", currText);
		}

		try {
			UpdateResponse response = solrClient.add(document);
			// solrServer.commit(false, false, true);
			if (addUntilCommitCounter >= ADD_UNTIL_COMMIT) {
				addUntilCommitCounter = 0;
				// solrServer.commit();
			} else {
				addUntilCommitCounter++;
			}
			if (logger.isDebugEnabled()) {
				logger.debug("addUntilCommitCounter: " + addUntilCommitCounter);
			}
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public SpellCheckResponse autoComplete(String term) throws SolrServerException {
		SolrQuery query = new SolrQuery();
		query.setRequestHandler("/tur_suggest");
		query.setQuery(term);
		QueryResponse queryResponse;
		try {
			queryResponse = solrClient.query(query);
			return queryResponse.getSpellCheckResponse();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public TurSEResults retrieveSolr(String txtQuery, List<String> fq, int currentPage, String sort, int rows)
			throws SolrServerException, NumberFormatException, JSONException {
		List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
				1);

		Map<String, TurSNSiteFieldExt> fieldExtMap = new HashMap<String, TurSNSiteFieldExt>();

		for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldExts) {
			fieldExtMap.put(turSNSiteFieldExt.getName(), turSNSiteFieldExt);
		}

		List<TurSNSiteFieldExt> turSNSiteRequiredFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndRequiredAndEnabled(turSNSite, 1, 1);
		Map<String, Object> requiredFields = new HashMap<String, Object>();

		for (TurSNSiteFieldExt turSNSiteRequiredFieldExt : turSNSiteRequiredFieldExts) {
			requiredFields.put(turSNSiteRequiredFieldExt.getName(), turSNSiteRequiredFieldExt.getDefaultValue());
		}

		TurSEResults turSEResults = new TurSEResults();
		if (rows <= 0) {
			rows = turSNSite.getRowsPerPage();
		}
		SimpleEntry<String, String> sortEntry = null;
		if (sort != null) {
			if (sort.toLowerCase().equals("relevance")) {
				sortEntry = null;
			} else if (sort.toLowerCase().equals("newest")) {
				sortEntry = new SimpleEntry<String, String>(turSNSite.getDefaultDateField(), "desc");
			} else if (sort.toLowerCase().equals("oldest")) {
				sortEntry = new SimpleEntry<String, String>(turSNSite.getDefaultDateField(), "asc");
			}
		}
		SolrQuery query = new SolrQuery();
		query.setQuery(txtQuery);

		if (sortEntry != null) {
			query.setSort(sortEntry.getKey(), sortEntry.getValue().equals("asc") ? ORDER.asc : ORDER.desc);
		}

		query.setRows(rows);
		query.setStart((currentPage * rows) - rows);

		// Facet
		List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);

		if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && turSNSiteFacetFieldExts.size() > 0) {
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

		// Highlighting
		List<TurSNSiteFieldExt> turSNSiteHlFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1);

		if (turSNSite.getHl() == 1 && turSNSiteHlFieldExts != null && turSNSiteHlFieldExts.size() > 0) {

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

		// MLT
		List<TurSNSiteFieldExt> turSNSiteMLTFieldExts = turSNSiteFieldExtRepository
				.findByTurSNSiteAndMltAndEnabled(turSNSite, 1, 1);
		if (turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts != null && turSNSiteMLTFieldExts.size() > 0) {

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

		String[] filterQueryArr = new String[fq.size()];
		filterQueryArr = fq.toArray(filterQueryArr);

		query.setFilterQueries(filterQueryArr);

		// System.out.println("Solr Query:" + query.toString());
		QueryResponse queryResponse;
		try {
			queryResponse = solrClient.query(query);

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

			List<TurSEResult> results = new ArrayList<TurSEResult>();

			// Facet
			if (turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && turSNSiteFacetFieldExts.size() > 0) {
				List<TurSEFacetResult> facetResults = new ArrayList<TurSEFacetResult>();
				for (FacetField facet : queryResponse.getFacetFields()) {
					TurSEFacetResult turSEFacetResult = new TurSEFacetResult();
					turSEFacetResult.setFacet(facet.getName());
					for (Count item : facet.getValues()) {
						turSEFacetResult.add(item.getName(),
								new TurSEFacetResultAttr(item.getName(), Long.valueOf(item.getCount()).intValue()));
					}
					facetResults.add(turSEFacetResult);
				}

				turSEResults.setFacetResults(facetResults);
			}

			List<TurSESimilarResult> similarResults = new ArrayList<TurSESimilarResult>();
			SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get("moreLikeThis");

			for (SolrDocument document : queryResponse.getResults()) {
				// HL
				Map<String, List<String>> hl = null;
				if (turSNSite.getHl() == 1 && turSNSiteHlFieldExts.size() > 0) {
					hl = queryResponse.getHighlighting().get((String) document.get("id"));
				}
				// MLT
				if (turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts.size() > 0) {
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
				TurSEResult turSEResult = new TurSEResult();
				for (Object requiredFieldObject : requiredFields.keySet().toArray()) {
					String requiredField = (String) requiredFieldObject;
					if (!document.containsKey(requiredField)) {
						document.addField(requiredField, requiredFields.get(requiredField));
					}
				}
				Map<String, Object> fields = new HashMap<String, Object>();
				for (String attribute : document.getFieldNames()) {
					Object attrValue = document.getFieldValue(attribute);

					if (fieldExtMap.containsKey(attribute)) {
						TurSNSiteFieldExt turSNSiteFieldExt = fieldExtMap.get(attribute);

						if (turSNSiteFieldExt.getType() == TurSEFieldType.STRING && hl != null
								&& hl.containsKey(attribute))
							attrValue = (String) hl.get(attribute).get(0);
						else {
							// System.out.println(turSNSiteFieldExt.getType() + " " + attribute);
							// attrValue = turSolrField.convertField(turSNSiteFieldExt.getType(),
							// attrValue);
						}
					}

					if (attribute != null && fields.containsKey(attribute)) {
						if (!(fields.get(attribute) instanceof List)) {
							List<Object> attributeValues = new ArrayList<Object>();
							attributeValues.add(fields.get(attribute));
							attributeValues.add(attrValue);
							fields.put(attribute, attributeValues);
						} else {
							((List<Object>) fields.get(attribute)).add(attrValue);
						}
					} else {
						fields.put(attribute, attrValue);
					}

					// fields.put(attribute, attrValue);

					turSEResult.setFields(fields);
				}
				results.add(turSEResult);
			}

			if (turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts != null && turSNSiteMLTFieldExts.size() > 0) {
				turSEResults.setSimilarResults(similarResults);
			}

			turSEResults.setResults(results);

			return turSEResults;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
