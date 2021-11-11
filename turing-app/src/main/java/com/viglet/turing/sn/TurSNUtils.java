/*
 * Copyright (C) 2016-2021 the original author or authors. 
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
package com.viglet.turing.sn;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.utils.StringUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.util.UriComponentsBuilder;

import com.viglet.turing.api.sn.search.TurSNParamType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;

public class TurSNUtils {
	private static final Logger logger = LogManager.getLogger(TurSNUtils.class);

	public static boolean hasCorrectedText(TurSESpellCheckResult turSESpellCheckResult) {
		return turSESpellCheckResult.isCorrected() && !StringUtils.isEmpty(turSESpellCheckResult.getCorrectedText());
	}

	public static boolean isAutoCorrectionEnabled(Integer currentPage, Integer autoCorrectionDisabled,
			TurSNSite turSNSite) {
		return autoCorrectionDisabled != 1 && currentPage == 1 && turSNSite.getSpellCheck() == 1
				&& turSNSite.getSpellCheckFixes() == 1;
	}

	public static URI requestToURI(HttpServletRequest request) {
		return UriComponentsBuilder.fromHttpRequest(new ServletServerHttpRequest(request)).build().toUri();
	}

	public static URI addOrReplaceParameter(URI uri, String paramName, String paramValue) {

		List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);

		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;

		for (NameValuePair nameValuePair : params) {
			if ((nameValuePair.getName().equals(paramName) && !alreadyExists)) {
				alreadyExists = true;
				addParameterToQueryString(sbQueryString, nameValuePair.getName(), paramValue);
			} else {
				addParameterToQueryString(sbQueryString, nameValuePair.getName(), nameValuePair.getValue());
			}
		}
		if (!alreadyExists) {
			addParameterToQueryString(sbQueryString, paramName, paramValue);
		}

		return modifiedURI(uri, sbQueryString);
	}

	public static URI addFilterQuery(URI uri, String fq) {
		List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
		StringBuffer sbQueryString = new StringBuffer();
		boolean alreadyExists = false;
		for (NameValuePair nameValuePair : params) {
			if ((nameValuePair.getValue().equals(fq)
					&& nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES))) {
				alreadyExists = true;
			}
			resetPagination(sbQueryString, nameValuePair);
		}
		if (!alreadyExists) {
			addParameterToQueryString(sbQueryString, TurSNParamType.FILTER_QUERIES, fq);
		}

		return modifiedURI(uri, sbQueryString);
	}

	public static URI removeFilterQuery(URI uri, String fq) {
		List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);
		StringBuffer sbQueryString = new StringBuffer();

		for (NameValuePair nameValuePair : params) {
			if (!(nameValuePair.getValue().equals(fq)
					&& nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES))) {
				resetPagination(sbQueryString, nameValuePair);
			}
		}

		return modifiedURI(uri, sbQueryString);
	}

	private static URI modifiedURI(URI uri, StringBuffer sbQueryString) {
		try {
			return new URI(uri.getRawPath() + "?" + removeAmpersand(sbQueryString));
		} catch (URISyntaxException e) {
			logger.error(e.getMessage(), e);
		}
		return uri;
	}

	private static String removeAmpersand(StringBuffer sbQueryString) {
		String queryString = sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);
		return queryString;
	}

	private static void addParameterToQueryString(StringBuffer sbQueryString, String name, String value) {
		sbQueryString.append(String.format("%s=%s&", name, URLEncoder.encode(value, StandardCharsets.UTF_8)));
	}

	private static void resetPagination(StringBuffer sbQueryString, NameValuePair nameValuePair) {
		if ((nameValuePair.getName().equals(TurSNParamType.PAGE))) {
			addParameterToQueryString(sbQueryString, nameValuePair.getName(), "1");
		} else {
			addParameterToQueryString(sbQueryString, nameValuePair.getName(), nameValuePair.getValue());
		}
	}
}
