/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
package com.viglet.turing.wem.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;

import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.beans.TuringTagMap;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.AttributeDefinitionData;
import com.vignette.as.client.common.DataType;
import com.vignette.as.client.common.ref.ManagedObjectRef;
import com.vignette.as.client.common.ref.ObjectTypeRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ContentType;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;

public class TuringUtils {
	private static final ContextLogger log = ContextLogger.getLogger(TuringUtils.class);

	private TuringUtils() {
		throw new IllegalStateException("TuringUtils");
	}

	public static String listToString(List<String> stringList) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (String s : stringList) {
			if (i++ != stringList.size() - 1) {
				sb.append(s);
				sb.append(", ");
			}
		}
		return sb.toString();
	}

	// Old turIndexAttMapToSet
	public static Set<TuringTag> turingTagMapToSet(TuringTagMap turingTagMap) {
		Set<TuringTag> turingTags = new HashSet<>();
		for (Entry<String, ArrayList<TuringTag>> entryCtd : turingTagMap.entrySet()) {
			for (TuringTag turingTag : entryCtd.getValue()) {
				turingTags.add(turingTag);
			}
		}
		return turingTags;
	}

	public static ContentInstance findContentInstanceByKey(ContentType contentType, String primaryKeyValue)
			throws Exception {

		ContentInstance ci = null;
		try {
			AttributeDefinitionData add = getKeyAttributeDefinitionData(contentType);
			DataType dt = add.getDataType();
			Object val = primaryKeyValue;
			if (dt.isInt() || dt.isNumerical() || dt.isTinyInt())
				val = Integer.valueOf(primaryKeyValue);
			ObjectTypeRef otr = new ObjectTypeRef(contentType);
			AttributeData atd = new AttributeData(add, val, otr);
			ManagedObjectRef ref = new ManagedObjectRef(otr, new AttributeData[] { atd });

			ci = (ContentInstance) ManagedObject.findById(ref);
		} catch (ApplicationException e) {
			log.error(e.getStackTrace());
		}

		return ci;
	}

	public static AttributeDefinitionData getKeyAttributeDefinitionData(ContentType ct) throws Exception {
		AttributeDefinitionData[] adds = ct.getData().getTopRelation().getKeyAttributeDefinitions();
		if (adds == null)
			throw new Exception("Failed to retrieve primary key definition", null);
		if (adds.length == 0)
			throw new Exception("No primary key found", null);
		if (adds.length > 1) {
			StringBuilder sb = new StringBuilder();
			sb.append("Works with one primary key only: ").append(adds.length);
			throw new Exception(sb.toString(), null);
		} else
			return adds[0];
	}

	public static void basicAuth(IHandlerConfiguration config, HttpPost post) {
		if (config.getLogin() != null && config.getLogin().trim().length() > 0) {
			String auth = String.format("%s:%s", config.getLogin(), config.getPassword());
			String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			String authHeader = "Basic " + encodedAuth;
			post.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
		}
	}

	public static void sendToTuring(TurSNJobItems turSNJobItems, IHandlerConfiguration config, AsLocaleData asLocale)
			throws IOException {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			if (!turSNJobItems.getTuringDocuments().isEmpty()) {

				String encoding = StandardCharsets.UTF_8.name();

				ObjectMapper mapper = new ObjectMapper();
				String jsonResult = mapper.writeValueAsString(turSNJobItems);

				Charset utf8Charset = StandardCharsets.UTF_8;
				Charset customCharset = Charset.forName(encoding);

				ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

				// decode UTF-8
				CharBuffer data = utf8Charset.decode(inputBuffer);

				// encode
				ByteBuffer outputBuffer = customCharset.encode(data);

				byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
				String jsonUTF8 = new String(outputData);

				HttpPost httpPost = new HttpPost(
						String.format("%s/api/sn/%s/import", config.getTuringURL(), config.getSNSite(asLocale)));

				StringEntity entity = new StringEntity(jsonUTF8, StandardCharsets.UTF_8);
				httpPost.setEntity(entity);
				httpPost.setHeader("Accept", "application/json");
				httpPost.setHeader("Content-type", "application/json");
				httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());

				basicAuth(config, httpPost);

				try (CloseableHttpResponse response = client.execute(httpPost)) {

					if (log.isDebugEnabled()) {
						log.debug(String.format("Viglet Turing Index Request URI: %s", httpPost.getURI()));
						log.debug(String.format("JSON: %s", jsonResult));
						log.debug(
								String.format("Viglet Turing indexer response HTTP result is: %s, for request uri: %s",
										response.getStatusLine().getStatusCode(), httpPost.getURI()));
						log.debug(String.format("Viglet Turing indexer response HTTP result is: %s",
								((HttpMethod) httpPost).getResponseBodyAsString()));
					}
					turSNJobItems.getTuringDocuments().clear();
				}
			}
		}
	}

}
