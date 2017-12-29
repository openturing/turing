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
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
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
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.field.TurSEFieldType;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.sn.TurSNFieldType;

@Component
@Transactional
public class TurSolr {

	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	static final Logger logger = LogManager.getLogger(TurSolr.class.getName());
	@Autowired
	TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurSolrField turSolrField;

	private TurSEInstance currSE = null;
	private Map<String, Object> attributes = null;
	private TurSNSite turSNSite = null;

	String currText = null;

	SolrServer solrServer = null;

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

	public void init(TurSEInstance turSEInstance) {
		this.setCurrSE(turSEInstance);

		if (turSEInstance != null) {
			solrServer = new HttpSolrServer("http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort()
					+ "/solr/" + turSNSite.getCore());
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
				.findById(Integer.parseInt(turConfigVarRepository.findById("DEFAULT_SE").getValue()));
		init(turSEInstance);
	}

	public void indexing() throws JSONException {
		logger.debug("Executing indexing ...");
		if (solrServer != null) {
			this.addDocument();
		}
	}

	public void addDocument() throws JSONException {

		SolrInputDocument document = new SolrInputDocument();

		// JSONObject jsonNLP = turNLPResults.getJsonResult();
		Map<String, Object> attributes = this.getAttributes();

		if (attributes != null) {

			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				String key = entry.getKey();
				Object attribute = entry.getValue();
				if (attribute != null) {
					if (attribute.getClass().getName().equals("java.lang.Integer")) {
						int intValue = (Integer) attribute;
						document.addField(key, intValue);
					} else if (attribute.getClass().getName().equals("org.json.JSONArray")) {
						JSONArray value = (JSONArray) attribute;
						if (value != null) {
							for (int i = 0; i < value.length(); i++) {
								document.addField(key, value.getString(i));
							}
						}
					} else if (attribute instanceof ArrayList) {
						ArrayList values = (ArrayList) attribute;
						if (values != null) {
							for (Object valueItem : values) {
								document.addField(key, turSolrField.convertFieldToString(valueItem));
							}
						}
					} else {
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
			UpdateResponse response = solrServer.add(document);
			solrServer.commit();
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public TurSEResults retrieveSolr(String txtQuery, List<String> fq, int currentPage, String sort)
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

		int rows = turSNSite.getRowsPerPage();
		SimpleEntry<String, String> sortEntry = null;
		if (sort != null) {
			if (sort.toLowerCase().equals("relevance")) {
				sortEntry = null;
			} else if (sort.toLowerCase().equals("newest")) {
				sortEntry = new SimpleEntry<String, String>("id", "desc");
			} else if (sort.toLowerCase().equals("oldest")) {
				sortEntry = new SimpleEntry<String, String>("id", "asc");
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
		QueryResponse queryResponse = solrServer.query(query);
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
					turSESimilarResult.setTitle(turSolrField.convertFieldToString(mltDocument.getFieldValue("title")));
					turSESimilarResult.setType(turSolrField.convertFieldToString(mltDocument.getFieldValue("type")));
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

					if (turSNSiteFieldExt.getType() == TurSEFieldType.STRING && hl != null && hl.containsKey(attribute))
						fields.put(attribute, (String) hl.get(attribute).get(0));
					else
						fields.put(attribute, turSolrField.convertField(turSNSiteFieldExt.getType(), attrValue));
				} else
					fields.put(attribute, attrValue);

				turSEResult.setFields(fields);
			}
			results.add(turSEResult);
		}

		if (turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts != null && turSNSiteMLTFieldExts.size() > 0) {
			turSEResults.setSimilarResults(similarResults);
		}

		turSEResults.setResults(results);

		return turSEResults;
	}
}
