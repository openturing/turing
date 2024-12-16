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
package com.viglet.turing.aem.core.servlets;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import org.osgi.framework.Constants;
import org.apache.sling.api.servlets.HttpConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.aem.core.TurAemSearchService;
import com.viglet.turing.aem.core.TurAemServerConfiguration;

@Component(service = Servlet.class, property = { Constants.SERVICE_DESCRIPTION + "=Turing Index Servlet",
		"sling.servlet.methods=" + HttpConstants.METHOD_GET, "sling.servlet.paths=" + "/bin/solr/push/pages" })
public class TurAemIndexContentToTuring extends SlingAllMethodsServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(TurAemIndexContentToTuring.class);

	@Reference
	TurAemServerConfiguration turAemConfigurationService;

	@Reference
	TurAemSearchService turAemSearchService;

	@Override
	protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		doPost(request, response);

	}

	@Override
	protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		String indexType = request.getParameter("indexType");
		final String protocol = turAemConfigurationService.getTuringProtocol();
		final String serverName = turAemConfigurationService.getTuringServerName();
		final String serverPort = turAemConfigurationService.getTuringServerPort();
		final String coreName = turAemConfigurationService.getTuringCoreName();
		final String pagesResourcePath = turAemConfigurationService.getContentPagePath();
		String URL = protocol + "://" + serverName + ":" + serverPort + "/solr/" + coreName;

		// Create an HTTPSolrClient instance
		HttpSolrClient server = new HttpSolrClient(URL);

		if (indexType.equalsIgnoreCase("indexpages")) {
			try {
				JSONArray indexPageData = turAemSearchService.crawlContent(pagesResourcePath, "cq:PageContent");

				boolean resultindexingPages = turAemSearchService.indexPagesToTuring(indexPageData, server);
				if (resultindexingPages == true) {
					response.getWriter().write("<h3>Successfully indexed content pages to Solr server </h3>");
				} else {
					response.getWriter().write("<h3>Something went wrong</h3>");
				}
			} catch (Exception e) {
				LOG.error("Exception due to", e);
				response.getWriter().write(
						"<h3>Something went wrong. Please make sure Solr server is configured properly in Felix</h3>");
			}

		} else {
			response.getWriter().write("<h3>Something went wrong</h3>");
		}

	}

}
