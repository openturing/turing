/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.utils;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import javax.xml.bind.DatatypeConverter;

import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import java.io.UnsupportedEncodingException;


/**
 * Client Utils
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurSNClientUtils {
	private static Logger logger = Logger.getLogger(TurSNClientUtils.class.getName());
	public static void basicAuth(HttpsURLConnection httpsURLConnection, TurUsernamePasswordCredentials credentials) {
		if (credentials != null && credentials.getUsername() != null) {
			String auth = String.format("%s:%s", credentials.getUsername(), credentials.getPassword());
			try{ 
				String encodedAuth = DatatypeConverter.printBase64Binary
			     	     (auth.getBytes("UTF-8"));
				String authHeader = "Basic " + encodedAuth;
				httpsURLConnection.setRequestProperty("Authorization", authHeader);
			} catch (UnsupportedEncodingException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

	public static Map<String, List<String>> splitQuery(URL url) throws UnsupportedEncodingException {
		final Map<String, List<String>> query_pairs = new LinkedHashMap<String, List<String>>();
		final String[] pairs = url.getQuery().split("&");
		for (String pair : pairs) {
		  final int idx = pair.indexOf("=");
		  final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
		  if (!query_pairs.containsKey(key)) {
			query_pairs.put(key, new LinkedList<String>());
		  }
		  final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
		  query_pairs.get(key).add(value);
		}
		return query_pairs;
	  }

	  public static void addURLParameter(URL url, String name, String value) {
		String parameter = String.format("%s=%s", name, value);
	   try {
		   URI uri = url.toURI();
		   url = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
				   uri.getQuery() == null ? parameter : uri.getQuery() + "&" + parameter, uri.getFragment()).toURL();
	   } catch (MalformedURLException e) {
		   logger.log(Level.SEVERE, e.getMessage(), e);
	   } catch (URISyntaxException e) {
		   logger.log(Level.SEVERE, e.getMessage(), e);
	   }
   }
}
