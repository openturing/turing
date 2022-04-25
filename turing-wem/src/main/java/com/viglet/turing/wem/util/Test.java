package com.viglet.turing.wem.util;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.viglet.turing.client.sn.TurSNQueryParamMap;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;

public class Test {
	public static Map<String, List<String>> splitQuery(URL url) throws UnsupportedEncodingException {
		final Map<String, List<String>> query_pairs = new LinkedHashMap<String, List<String>>();
		final String[] pairs = url.getQuery().split("&");
		for (String pair : pairs) {
			final int idx = pair.indexOf("=");
			final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
			if (!query_pairs.containsKey(key)) {
				query_pairs.put(key, new LinkedList<String>());
			}
			final String value = idx > 0 && pair.length() > idx + 1
					? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
					: null;
			query_pairs.get(key).add(value);
		}
		return query_pairs;
	}

	public static void test() {
		try {
			for (Entry<String, List<String>> queryItem : splitQuery(new URL("example.com")).entrySet()) {
				queryItem.getKey();
				queryItem.getValue();
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	 public static TurSNQueryParamMap getQueryParams() {
		    String apiURL = "112";

		    if (apiURL != null) {
		      TurSNQueryParamMap queryParams = new TurSNQueryParamMap();
		      try {
		        for (Entry<String, List<String>> param : splitQuery(new URL(apiURL)).entrySet()) {
		        	queryParams.get(param.getKey()).addAll(param.getValue());
		        }
		      } catch (UnsupportedEncodingException e) {
		        logger.log(Level.SEVERE, e.getMessage(), e);
		      } catch (MalformedURLException e) {
		        logger.log(Level.SEVERE, e.getMessage(), e);
		      }
		      return queryParams;
		    }
		    return null;
		  }
	 
	 
	 public static void addURLParameter(URL url, String name, String value) {
		 
		 	URL url1 = new URL("ssss");
		 	
		 	String parameter = String.format("%s=%s", name, value);
		    URI uri = url.toURI();
		    try {
				url = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
						uri.getQuery() == null ? parameter : uri.getQuery() + "&" + parameter, uri.getFragment()).toURL();
			} catch (MalformedURLException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
			} catch (URISyntaxException e) {
				logger.log(Level.SEVERE, e.getMessage(), e);
			}
		}
}
