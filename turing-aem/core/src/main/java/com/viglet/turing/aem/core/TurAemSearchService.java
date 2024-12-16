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
package com.viglet.turing.aem.core;

import java.io.IOException;

import javax.jcr.RepositoryException;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

import com.day.cq.search.result.SearchResult;

public interface TurAemSearchService {

	JSONArray crawlContent(String resourcePath, String resourceType);

	JSONArray createPageMetadataArray(SearchResult results) throws RepositoryException;

	JSONObject createPageMetadataObject(Resource pageContent);

	boolean indexPageToTuring(JSONObject indexPageData, HttpSolrClient server)
			throws JSONException, SolrServerException, IOException;

	boolean indexPagesToTuring(JSONArray indexPageData, HttpSolrClient server)
			throws JSONException, SolrServerException, IOException;

}