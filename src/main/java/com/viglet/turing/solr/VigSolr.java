package com.viglet.turing.solr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

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
import org.json.JSONObject;

import com.viglet.turing.entity.VigEntityProcessor;
import com.viglet.turing.nlp.VigNLP;
import com.viglet.turing.nlp.VigNLPResults;
import com.viglet.turing.persistence.model.TurNLPSolution;
import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.persistence.model.VigServicesNLPEntity;
import com.viglet.turing.se.facet.VigSEFacetMap;
import com.viglet.turing.se.facet.VigSEFacetMaps;
import com.viglet.turing.se.facet.VigSEFacetResult;
import com.viglet.turing.se.facet.VigSEFacetResultAttr;
import com.viglet.turing.se.field.VigSEFieldMap;
import com.viglet.turing.se.field.VigSEFieldMaps;
import com.viglet.turing.se.result.VigSEResult;
import com.viglet.turing.se.result.VigSEResultAttr;
import com.viglet.turing.se.result.VigSEResults;
import com.viglet.turing.se.similar.VigSESimilarResult;
import com.viglet.turing.se.similar.VigSESimilarResultAttr;
import com.viglet.turing.service.VigServiceUtil;

public class VigSolr {
	static final Logger logger = LogManager.getLogger(VigSolr.class.getName());
	
	private int currNLP = 0;
	private int currSE = 0;
	private JSONObject jsonAttributes = null;

	String currText = null;
	VigService vigServiceSE = null;
	VigService vigServiceNLP = null;
	TurNLPSolution vigNLPSolution = null;
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

	public void init(int nlp, int se) {
		this.setCurrNLP(nlp);

		this.setCurrSE(se);
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryServiceSE = em.createQuery("SELECT s FROM VigService s where s.type = :type and s.id = :id ")
				.setParameter("type", 3).setParameter("id", currSE);

		vigServiceSE = (VigService) queryServiceSE.getSingleResult();

		solrServer = new HttpSolrServer(
				"http://" + vigServiceSE.getHost() + ":" + vigServiceSE.getPort() + "/solr/turing");

	}

	public VigSolr(int nlp, int se, String text) {
		super();
		init(nlp, se);
		this.setCurrText(text);
	}

	public VigSolr(int nlp, int se, JSONObject jsonAttributes) {
		super();
		init(nlp, se);
		this.setJsonAttributes(jsonAttributes);
		this.setCurrText(null);
	}

	public VigSolr() {
		super();
		VigServiceUtil vigServiceUtil = new VigServiceUtil();
		init(vigServiceUtil.getNLPDefault(), vigServiceUtil.getSEDefault());

	}

	public String indexing() {
		logger.debug("Executing indexing ...");	
		VigNLP vigNLP = null;

		if (this.getJsonAttributes() != null) {
			vigNLP = new VigNLP(currNLP, this.getJsonAttributes());
		} else {
			vigNLP = new VigNLP(currNLP, currText);
		}

		VigNLPResults vigNLPResults = vigNLP.retrieveNLP();

		this.addDocument(vigNLPResults);

		return vigNLPResults.getJsonResult().toString();
	}

	public void addDocument(VigNLPResults vigNLPResults) {

		SolrInputDocument document = new SolrInputDocument();

		JSONObject jsonNLP = vigNLPResults.getJsonResult();
		JSONObject jsonAttributes = vigNLPResults.getJsonAttributes();

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

		for (VigServicesNLPEntity vigNLPEntity : vigNLPResults.getVigNLPServicesEntity()) {
			if (jsonNLP.has(vigNLPEntity.getTurEntity().getCollectionName())) {
				JSONArray jsonEntity = jsonNLP.getJSONArray(vigNLPEntity.getTurEntity().getCollectionName());

				if (jsonEntity.length() > 0) {
					for (int i = 0; i < jsonEntity.length(); i++) {
						document.addField("turing_entity_" + vigNLPEntity.getTurEntity().getInternalName(),
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

	public VigSEResults retrieveSolr(String txtQuery, List<String> fq, int currentPage) throws SolrServerException {
		// 20:44:41 INFO - [mgmt] webapp=/solr path=/querydispatcher
		// params={facet=true&facet.offset=0&facet.limit=30&rows=0&
		// facet.filter=true&facet.filter.limit=10&q=empreender&
		// facet.field=sebna_uf} hits=3008 status=0 QTime=2

		int rows = 10;
		String sort = "relevant";
		VigSEResults vigSEResults = new VigSEResults();

		Map<String, VigSEFieldMap> fieldMap = new VigSEFieldMaps().getFieldMaps();

		Map<String, Object> requiredFields = new HashMap<String, Object>();
		for (Object fieldMapObject : fieldMap.values().toArray()) {
			VigSEFieldMap fieldMapRequired = (VigSEFieldMap) fieldMapObject;
			if (fieldMapRequired.getRequired() != null && fieldMapRequired.getRequired().isRequired()) {
				requiredFields.put(fieldMapRequired.getField(), fieldMapRequired.getRequired().getDefaultValue());
			}
		}

		SolrQuery query = new SolrQuery();
		query.setQuery(txtQuery);
		query.setRows(rows);
		query.setStart((currentPage*rows)-rows);

		query.setFacet(true);
		query.setFacetLimit(30);
		query.setFacetMinCount(1);
		query.setFacetSort("count");

		Map<String, VigSEFacetMap> facetMap = new VigSEFacetMaps().getFacetMaps();

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
		vigSEResults.setNumFound(queryResponse.getResults().getNumFound());
		vigSEResults.setElapsedTime(queryResponse.getElapsedTime());
		vigSEResults.setqTime(queryResponse.getQTime());
		vigSEResults.setStart(queryResponse.getResults().getStart());
		vigSEResults.setQueryString(query.getQuery());
		vigSEResults.setSort(sort);
		vigSEResults.setLimit(rows);

		int pageCount = (int) Math.ceil(vigSEResults.getNumFound() / (double) vigSEResults.getLimit());
		vigSEResults.setPageCount(pageCount);
		vigSEResults.setCurrentPage(currentPage);

		List<VigSEResult> results = new ArrayList<VigSEResult>();
		List<VigSEFacetResult> facetResults = new ArrayList<VigSEFacetResult>();
		List<VigSESimilarResult> similarResults = new ArrayList<VigSESimilarResult>();

		for (FacetField facet : queryResponse.getFacetFields()) {
			VigSEFacetResult vigSEFacetResult = new VigSEFacetResult();
			vigSEFacetResult.setFacet(facet.getName());
			for (Count item : facet.getValues()) {
				vigSEFacetResult.add(item.getName(),
						new VigSEFacetResultAttr(item.getName(), Long.valueOf(item.getCount()).intValue()));
			}
			facetResults.add(vigSEFacetResult);
		}

		// MLT
		SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get("moreLikeThis");

		for (SolrDocument document : queryResponse.getResults()) {
			Map<String, List<String>> hl = queryResponse.getHighlighting().get((String) document.get("id"));

			SolrDocumentList mltDocumentList = (SolrDocumentList) mltResp.get((String) document.get("id"));
			for (SolrDocument mltDocument : mltDocumentList) {
				VigSESimilarResult vigSESimilarResult = new VigSESimilarResult();
				vigSESimilarResult.add("id",
						new VigSESimilarResultAttr("id", (String) mltDocument.getFieldValue("id")));
				vigSESimilarResult.add("title", new VigSESimilarResultAttr("title",
						(String) ((ArrayList<?>) mltDocument.getFieldValue("title")).get(0)));
				vigSESimilarResult.add("type", new VigSESimilarResultAttr("type",
						(String) ((ArrayList<?>) mltDocument.getFieldValue("type")).get(0)));
				vigSESimilarResult.add("url", new VigSESimilarResultAttr("url",
						(String) ((ArrayList<?>) mltDocument.getFieldValue("url")).get(0)));
				similarResults.add(vigSESimilarResult);
			}

			VigSEResult vigSEResult = new VigSEResult();
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
					VigSEFieldMap vigSEFieldMap = fieldMap.get(attribute);
					switch (vigSEFieldMap.getType()) {
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
					vigSEResult.add(attribute, new VigSEResultAttr(attribute, jsonObject));
				} else {
					jsonObject.put(attribute, attrValue);
					vigSEResult.add(attribute, new VigSEResultAttr(attribute, jsonObject));
				}
			}
			results.add(vigSEResult);
		}
		vigSEResults.setResults(results);
		vigSEResults.setFacetResults(facetResults);
		vigSEResults.setSimilarResults(similarResults);
		return vigSEResults;
	}
}
