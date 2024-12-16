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

import javax.servlet.ServletException;
import javax.servlet.Servlet;
import org.osgi.framework.Constants;
import org.apache.sling.api.servlets.HttpConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.aem.core.TurAemServerConfiguration;

@Component(service = Servlet.class, property = { Constants.SERVICE_DESCRIPTION + "=turAem Delete Index",
		"sling.servlet.methods=" + HttpConstants.METHOD_POST,
		"sling.servlet.paths=" + "/bin/turAem/delete/all/indexes" })
public class TurAemDeleteIndexesFromTuring extends SlingAllMethodsServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(TurAemDeleteIndexesFromTuring.class);
	@Reference
	TurAemServerConfiguration turAemConfigurationService;

	@Override
	protected void doPost(final SlingHttpServletRequest reqest, final SlingHttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("text/html");
		final String protocol = turAemConfigurationService.getTuringProtocol();
		final String serverName = turAemConfigurationService.getTuringServerName();
		final String serverPort = turAemConfigurationService.getTuringServerPort();
		final String coreName = turAemConfigurationService.getTuringCoreName();
		String URL = protocol + "://" + serverName + ":" + serverPort + "/solr/" + coreName;
		HttpSolrClient server = new HttpSolrClient(URL);
		try {
			server.deleteByQuery("*:*");
			server.commit();
			server.close();
			response.getWriter().write("<h3>Deleted all the indexes from turAem server </h3>");
		} catch (SolrServerException e) {
			LOG.error("Exception due to", e);
		}

	}
}
