/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
package com.viglet.turing.client.sn.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turing Semantic Navigation Utilities.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */
public class TurSNJobUtils {
	private static final Logger logger = Logger.getLogger(TurSNJobUtils.class.getName());
	private static final String TYPE_ATTRIBUTE = "type";
	private static final String PROVIDER_ATTRIBUTE = "source_apps";

	public static void importItems(TurSNJobItems turSNJobItems, TurSNServer turSNServer, boolean showOutput) {

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			String jsonResult = new ObjectMapper().registerModule(new Jdk8Module()).writeValueAsString(turSNJobItems);
			ByteBuffer buffer = StandardCharsets.UTF_8.encode(jsonResult);
			String jsonUTF8  = StandardCharsets.UTF_8.decode(buffer).toString();

			HttpPost httpPost = new HttpPost(
					String.format("%s/api/sn/%s/import", turSNServer.getServerURL(), turSNServer.getSiteName()));
			if (showOutput) {
				System.out.println(jsonUTF8);
			}
			httpPost.setEntity(new StringEntity(jsonUTF8, StandardCharsets.UTF_8));
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");
			httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());

			TurSNClientUtils.authentication(httpPost, turSNServer.getCredentials(), turSNServer.getApiKey());
			try (CloseableHttpResponse response = client.execute(httpPost)) {
				if (logger.isLoggable(Level.FINE)) {
					logger.fine(String.format("Viglet Turing Index Request URI: %s", httpPost.getUri()));
					logger.fine(String.format("JSON: %s", jsonResult));
					logger.fine(String.format("Viglet Turing indexer response HTTP result is: %s, for request uri: %s",
							response.getCode(), httpPost.getUri()));
					logger.fine(String.format("Viglet Turing indexer response HTTP result is: %s",
							httpPost.getEntity().toString()));
				}

			} catch (URISyntaxException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
            }
        } catch (IOException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public static void deleteItemsByType(TurSNServer turSNServer, String typeName) {
		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.DELETE, turSNServer.getLocale());
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(TYPE_ATTRIBUTE, typeName);
		attributes.put(PROVIDER_ATTRIBUTE, turSNServer.getProviderName());
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		importItems(turSNJobItems, turSNServer, false);
	}
}
