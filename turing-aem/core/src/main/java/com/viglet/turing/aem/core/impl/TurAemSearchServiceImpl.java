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
package com.viglet.turing.aem.core.impl;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.aem.core.TurAemSearchService;
import com.viglet.turing.aem.core.TurAemServerConfiguration;
import com.viglet.turing.aem.core.utils.TurAemUtils;
import com.day.cq.search.PredicateGroup;
import com.day.cq.search.Query;
import com.day.cq.search.QueryBuilder;
import com.day.cq.search.result.Hit;
import com.day.cq.search.result.SearchResult;

@Component
public class TurAemSearchServiceImpl implements TurAemSearchService {

	private static final Logger LOG = LoggerFactory.getLogger(TurAemSearchServiceImpl.class);

	@Reference
	private QueryBuilder queryBuilder;

	@Reference
	private SlingRepository repository;

	@Reference
	TurAemServerConfiguration turAemConfigurationService;

	@Override
	public JSONArray crawlContent(String resourcePath, String resourceType) {

		Map<String, String> params = new HashMap<String, String>();
		params.put("path", resourcePath);
		params.put("type", resourceType);
		params.put("p.offset", "0");
		params.put("p.limit", "10000");

		Session session = null;

		try {
			session = repository.loginAdministrative(null);
			Query query = queryBuilder.createQuery(PredicateGroup.create(params), session);

			SearchResult searchResults = query.getResult();

			LOG.info("Found '{}' matches for query", searchResults.getTotalMatches());
			if (resourceType.equalsIgnoreCase("cq:PageContent")) {
				return createPageMetadataArray(searchResults);
			}

		} catch (RepositoryException e) {
			LOG.error("Exception due to", e);
		} finally {
			if (session.isLive() || session != null) {
				session.logout();
			}
		}
		return null;

	}

	@Override
	public JSONArray createPageMetadataArray(SearchResult results) throws RepositoryException {
		JSONArray solrDocs = new JSONArray();
		for (Hit hit : results.getHits()) {
			Resource pageContent = hit.getResource();
			ValueMap properties = pageContent.adaptTo(ValueMap.class);
			String isPageIndexable = properties.get("notsolrindexable", String.class);
			if (null != isPageIndexable && isPageIndexable.equals("true"))
				continue;
			JSONObject propertiesMap = createPageMetadataObject(pageContent);
			solrDocs.put(propertiesMap);
		}

		return solrDocs;

	}

	@Override
	public JSONObject createPageMetadataObject(Resource pageContent) {
		Map<String, Object> propertiesMap = new HashMap<String, Object>();
		propertiesMap.put("id", pageContent.getParent().getPath());
		propertiesMap.put("url", pageContent.getParent().getPath() + ".html");
		ValueMap properties = pageContent.adaptTo(ValueMap.class);
		String pageTitle = properties.get("jcr:title", String.class);
		if (StringUtils.isEmpty(pageTitle)) {
			pageTitle = pageContent.getParent().getName();
		}
		propertiesMap.put("title", pageTitle);
		propertiesMap.put("description", TurAemUtils.checkNull(properties.get("jcr:description", String.class)));
		propertiesMap.put("publishDate", TurAemUtils.checkNull(properties.get("publishdate", String.class)));
		propertiesMap.put("body", "");
		propertiesMap.put("lastModified", TurAemUtils.solrDate(properties.get("cq:lastModified", Calendar.class)));
		propertiesMap.put("contentType", "page");
		propertiesMap.put("tags", TurAemUtils.getPageTags(pageContent));
		return new JSONObject(propertiesMap);
	}

	@Override
	public boolean indexPagesToTuring(JSONArray indexPageData, HttpSolrClient server)
			throws JSONException, SolrServerException, IOException {

		if (null != indexPageData) {

			for (int i = 0; i < indexPageData.length(); i++) {
				JSONObject pageJsonObject = indexPageData.getJSONObject(i);
				SolrInputDocument doc = createPageSolrDoc(pageJsonObject);
				server.add(doc);
			}
			server.commit();
			return true;
		}

		return false;
	}

	@Override
	public boolean indexPageToTuring(JSONObject indexPageData, HttpSolrClient server)
			throws JSONException, SolrServerException, IOException {
		if (null != indexPageData) {
			SolrInputDocument doc = createPageSolrDoc(indexPageData);
			server.add(doc);
			server.commit();
			return true;
		}

		return false;
	}

	private SolrInputDocument createPageSolrDoc(JSONObject pageJsonObject) throws JSONException {

		SolrInputDocument doc = new SolrInputDocument();
		doc.addField("id", pageJsonObject.get("id"));
		doc.addField("title", pageJsonObject.get("title"));
		doc.addField("body", pageJsonObject.get("body"));
		doc.addField("url", pageJsonObject.get("url"));
		doc.addField("description", pageJsonObject.get("description"));
		doc.addField("lastModified", pageJsonObject.get("lastModified"));
		doc.addField("contentType", pageJsonObject.get("contentType"));
		doc.addField("tags", pageJsonObject.get("tags"));
		return doc;

	}

}
