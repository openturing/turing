package com.viglet.turing.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
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
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.nlp.TurNLPResults;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.se.facet.TurSEFacetMap;
import com.viglet.turing.se.facet.TurSEFacetMaps;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.field.TurSEFieldMap;
import com.viglet.turing.se.field.TurSEFieldMaps;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResultAttr;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.se.similar.TurSESimilarResult;
import com.viglet.turing.se.similar.TurSESimilarResultAttr;

@ComponentScan
@Transactional
public class TurSolr {

	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	static final Logger logger = LogManager.getLogger(TurSolr.class.getName());
	@Autowired
	TurNLP turNLP;
	
	private int currNLP = 0;
	private int currSE = 0;
	private JSONObject jsonAttributes = null;

	String currText = null;
	TurNLPVendor turNLPVendor = null;
	SolrServer solrServer = null;

	public JSONObject getJsonAttributes() {
		return jsonAttributes;
	}

	public void setJsonAttributes(JSONObject jsonAttributes) {
		this.jsonAttributes = jsonAttributes;
	}

	public int getCurrNLP() {
		return currNLP;
	}

	public void setCurrNLP(int currNLP) {
		this.currNLP = currNLP;
	}

	public int getCurrSE() {
		return currSE;
	}

	public void setCurrSE(int currSE) {
		this.currSE = currSE;
	}

	public String getCurrText() {
		return currText;
	}

	public void setCurrText(String currText) {
		this.currText = currText;
	}

	public void init(int nlpInstanceId, int se) {
		this.setCurrNLP(nlpInstanceId);

		this.setCurrSE(se);

		TurSEInstance turSEInstance = turSEInstanceRepository.findOne(se);

		if (turSEInstance != null) {
			solrServer = new HttpSolrServer(
					"http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort() + "/solr/turing");
		}
	}

	public TurSolr(int nlpInstanceId, int se, String text) {
		super();
		init(nlpInstanceId, se);
		this.setCurrText(text);
	}

	public TurSolr(int nlpInstanceId, int se, JSONObject jsonAttributes) {
		super();
		init(nlpInstanceId, se);
		this.setJsonAttributes(jsonAttributes);
		this.setCurrText(null);
	}

	public TurSolr() {
		super();
		init(Integer.parseInt(turConfigVarRepository.getOne("DEFAULT_NLP").getValue()),
				Integer.parseInt(turConfigVarRepository.getOne("DEFAULT_SE").getValue()));

	}

	public String indexing() throws JSONException {
		logger.debug("Executing indexing ...");
		if (this.getJsonAttributes() != null) {
			turNLP.startup(currNLP, this.getJsonAttributes());
		} else {
			turNLP.startup(currNLP, currText);
		}

		TurNLPResults turNLPResults = turNLP.retrieveNLP();
		if (solrServer != null) {
			this.addDocument(turNLPResults);
		}

		return turNLPResults.getJsonResult().toString();
	}

	public void addDocument(TurNLPResults turNLPResults) throws JSONException {

		SolrInputDocument document = new SolrInputDocument();

		JSONObject jsonNLP = turNLPResults.getJsonResult();
		JSONObject jsonAttributes = turNLPResults.getJsonAttributes();

		if (jsonAttributes != null) {
			Iterator<?> keys = jsonAttributes.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				String value = (String) jsonAttributes.get(key);
				document.addField(key, value);
			}
		} else {
			UUID documentId = UUID.randomUUID();
			document.addField("id", documentId);
			document.addField("text", currText);
		}

		for (TurNLPInstanceEntity turNLPEntity : turNLPResults.getTurNLPInstanceEntities()) {
			if (jsonNLP.has(turNLPEntity.getTurNLPEntity().getCollectionName())) {
				JSONArray jsonEntity = jsonNLP.getJSONArray(turNLPEntity.getTurNLPEntity().getCollectionName());

				if (jsonEntity.length() > 0) {
					for (int i = 0; i < jsonEntity.length(); i++) {
						document.addField("turing_entity_" + turNLPEntity.getTurNLPEntity().getInternalName(),
								jsonEntity.getString(i));
					}
				}
			}
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

	public TurSEResults retrieveSolr(String txtQuery, List<String> fq, int currentPage)
			throws SolrServerException, NumberFormatException, JSONException {
		// 20:44:41 INFO - [mgmt] webapp=/solr path=/querydispatcher
		// params={facet=true&facet.offset=0&facet.limit=30&rows=0&
		// facet.filter=true&facet.filter.limit=10&q=empreender&
		// facet.field=sebna_uf} hits=3008 status=0 QTime=2

		int rows = 10;
		String sort = "relevant";
		TurSEResults turSEResults = new TurSEResults();

		Map<String, TurSEFieldMap> fieldMap = new TurSEFieldMaps().getFieldMaps();

		Map<String, Object> requiredFields = new HashMap<String, Object>();
		for (Object fieldMapObject : fieldMap.values().toArray()) {
			TurSEFieldMap fieldMapRequired = (TurSEFieldMap) fieldMapObject;
			if (fieldMapRequired.getRequired() != null && fieldMapRequired.getRequired().isRequired()) {
				requiredFields.put(fieldMapRequired.getField(), fieldMapRequired.getRequired().getDefaultValue());
			}
		}

		SolrQuery query = new SolrQuery();
		query.setQuery(txtQuery);
		query.setRows(rows);
		query.setStart((currentPage * rows) - rows);

		query.setFacet(true);
		query.setFacetLimit(30);
		query.setFacetMinCount(1);
		query.setFacetSort("count");

		Map<String, TurSEFacetMap> facetMap = new TurSEFacetMaps().getFacetMaps();

		for (Object facet : facetMap.keySet().toArray()) {
			query.addFacetField((String) facet);
		}

		query.set(MoreLikeThisParams.MLT, true);
		query.set(MoreLikeThisParams.MATCH_INCLUDE, true);
		query.set(MoreLikeThisParams.MIN_DOC_FREQ, 1);
		query.set(MoreLikeThisParams.MIN_TERM_FREQ, 1);
		query.set(MoreLikeThisParams.MIN_WORD_LEN, 7);
		query.set(MoreLikeThisParams.BOOST, false);
		query.set(MoreLikeThisParams.MAX_QUERY_TERMS, 1000);
		query.set(MoreLikeThisParams.SIMILARITY_FIELDS, "title,text,abstract");

		query.setHighlight(true).setHighlightSnippets(1);
		query.setParam("hl.fl", "title,abstract");
		query.setParam("hl.fragsize", "0");
		query.setParam("hl.simple.pre", "<mark>");
		query.setParam("hl.simple.post", "</mark>");

		String[] filterQueryArr = new String[fq.size()];
		filterQueryArr = fq.toArray(filterQueryArr);

		query.setFilterQueries(filterQueryArr);

		System.out.println("Solr Query:" + query.toString());
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
		List<TurSEFacetResult> facetResults = new ArrayList<TurSEFacetResult>();
		List<TurSESimilarResult> similarResults = new ArrayList<TurSESimilarResult>();

		for (FacetField facet : queryResponse.getFacetFields()) {
			TurSEFacetResult turSEFacetResult = new TurSEFacetResult();
			turSEFacetResult.setFacet(facet.getName());
			for (Count item : facet.getValues()) {
				turSEFacetResult.add(item.getName(),
						new TurSEFacetResultAttr(item.getName(), Long.valueOf(item.getCount()).intValue()));
			}
			facetResults.add(turSEFacetResult);
		}

		// MLT
		SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get("moreLikeThis");

		for (SolrDocument document : queryResponse.getResults()) {
			Map<String, List<String>> hl = queryResponse.getHighlighting().get((String) document.get("id"));

			SolrDocumentList mltDocumentList = (SolrDocumentList) mltResp.get((String) document.get("id"));
			for (SolrDocument mltDocument : mltDocumentList) {
				TurSESimilarResult turSESimilarResult = new TurSESimilarResult();
				turSESimilarResult.add("id",
						new TurSESimilarResultAttr("id", (String) mltDocument.getFieldValue("id")));
				turSESimilarResult.add("title", new TurSESimilarResultAttr("title",
						(String) ((ArrayList<?>) mltDocument.getFieldValue("title")).get(0)));
				turSESimilarResult.add("type", new TurSESimilarResultAttr("type",
						(String) ((ArrayList<?>) mltDocument.getFieldValue("type")).get(0)));
				turSESimilarResult.add("url", new TurSESimilarResultAttr("url",
						(String) ((ArrayList<?>) mltDocument.getFieldValue("url")).get(0)));
				similarResults.add(turSESimilarResult);
			}

			TurSEResult turSEResult = new TurSEResult();
			for (Object requiredFieldObject : requiredFields.keySet().toArray()) {
				String requiredField = (String) requiredFieldObject;
				if (!document.containsKey(requiredField)) {
					document.addField(requiredField, requiredFields.get(requiredField));
				}
			}
			for (String attribute : document.getFieldNames()) {
				Object attrValue = document.getFieldValue(attribute);
				JSONObject jsonObject = new JSONObject();
				if (fieldMap.containsKey(attribute)) {
					TurSEFieldMap turSEFieldMap = fieldMap.get(attribute);
					switch (turSEFieldMap.getType()) {
					case INT:
						if (attrValue instanceof String) {
							jsonObject.put(attribute, Integer.parseInt((String) attrValue));
						} else if (attrValue instanceof ArrayList) {
							ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
							if (arrAttValue.get(0) instanceof Long) {
								jsonObject.put(attribute, ((Long) arrAttValue.get(0)).intValue());
							} else if (arrAttValue.get(0) instanceof String) {
								jsonObject.put(attribute, Integer.parseInt((String) arrAttValue.get(0)));
							} else {
								jsonObject.put(attribute, arrAttValue.get(0));
							}

						} else if (attrValue instanceof Long) {
							jsonObject.put(attribute, ((Long) attrValue).intValue());
						} else {
							jsonObject.put(attribute, attrValue);
						}
						break;
					case LONG:
						if (attrValue instanceof String) {
							jsonObject.put(attribute, Long.parseLong((String) attrValue));
						} else if (attrValue instanceof ArrayList) {
							ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
							if (arrAttValue.get(0) instanceof String) {
								jsonObject.put(attribute, Long.parseLong((String) arrAttValue.get(0)));
							} else if (arrAttValue.get(0) instanceof Long) {
								jsonObject.put(attribute, (Long) arrAttValue.get(0));
							} else {
								jsonObject.put(attribute, arrAttValue.get(0));
							}
						} else if (attrValue instanceof Long) {
							jsonObject.put(attribute, (Long) attrValue);
						} else {
							jsonObject.put(attribute, attrValue);
						}
						break;
					case STRING:
						if (hl.containsKey(attribute)) {
							jsonObject.put(attribute, (String) hl.get(attribute).get(0));
						} else {
							if (attrValue instanceof String) {
								jsonObject.put(attribute, (String) attrValue);
							} else if (attrValue instanceof ArrayList) {
								ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;

								if (arrAttValue.get(0) instanceof String) {
									jsonObject.put(attribute, (String) arrAttValue.get(0));
								} else if (arrAttValue.get(0) instanceof Long) {
									jsonObject.put(attribute, ((Long) arrAttValue.get(0)).toString());
								} else {
									jsonObject.put(attribute, arrAttValue.get(0));
								}
							} else if (attrValue instanceof Long) {
								jsonObject.put(attribute, ((Long) attrValue).toString());
							} else {
								jsonObject.put(attribute, attrValue);
							}
						}
						break;
					case ARRAY:
						if (attrValue instanceof String) {
							String[] array = { (String) attrValue };
							jsonObject.put(attribute, array);
						} else if (attrValue instanceof ArrayList) {
							jsonObject.put(attribute, attrValue);
						} else if (attrValue instanceof Long) {
							Long[] array = { (Long) attrValue };
							jsonObject.put(attribute, array);
						} else {
							jsonObject.put(attribute, attrValue);
						}
						break;
					case DATE:
						if (attrValue instanceof String) {
							jsonObject.put(attribute, (String) attrValue);
						} else if (attrValue instanceof ArrayList) {
							ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
							jsonObject.put(attribute, arrAttValue.get(0));
						} else if (attrValue instanceof Long) {
							jsonObject.put(attribute, ((Long) attrValue).toString());
						} else {
							jsonObject.put(attribute, attrValue);
						}
						break;
					case BOOL:
						if (attrValue instanceof String) {
							jsonObject.put(attribute, Boolean.parseBoolean((String) attrValue));
						} else if (attrValue instanceof ArrayList) {
							ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
							if (arrAttValue.get(0) instanceof String) {
								jsonObject.put(attribute, Boolean.parseBoolean((String) arrAttValue.get(0)));
							} else if (arrAttValue.get(0) instanceof Long) {
								if (((Long) arrAttValue.get(0)) > 0) {
									jsonObject.put(attribute, true);
								} else {
									jsonObject.put(attribute, false);
								}
							} else {
								jsonObject.put(attribute, attrValue);
							}
						} else if (attrValue instanceof Long) {
							if (((Long) attrValue) > 0) {
								jsonObject.put(attribute, true);
							} else {
								jsonObject.put(attribute, false);
							}
						} else {
							jsonObject.put(attribute, attrValue);
						}
						break;
					default:
						if (attrValue instanceof String) {
							jsonObject.put(attribute, (String) attrValue);
						} else if (attrValue instanceof ArrayList) {
							ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
							jsonObject.put(attribute, arrAttValue.get(0));
						} else if (attrValue instanceof Long) {
							jsonObject.put(attribute, ((Long) attrValue).toString());
						} else {
							jsonObject.put(attribute, attrValue);
						}
						break;
					}
					turSEResult.add(attribute, new TurSEResultAttr(attribute, jsonObject));
				} else {
					jsonObject.put(attribute, attrValue);
					turSEResult.add(attribute, new TurSEResultAttr(attribute, jsonObject));
				}
			}
			results.add(turSEResult);
		}
		turSEResults.setResults(results);
		turSEResults.setFacetResults(facetResults);
		turSEResults.setSimilarResults(similarResults);
		return turSEResults;
	}
}
