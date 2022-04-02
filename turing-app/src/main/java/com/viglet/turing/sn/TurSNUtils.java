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
package com.viglet.turing.sn;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.utils.StringUtils;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.util.UriComponentsBuilder;

import com.viglet.turing.api.sn.bean.TurSNSiteSearchDocumentBean;
import com.viglet.turing.api.sn.bean.TurSNSiteSearchDocumentMetadataBean;
import com.viglet.turing.api.sn.search.TurSNParamType;
import com.viglet.turing.api.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.solr.TurSolrField;

public class TurSNUtils {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	public static final String TURING_ENTITY = "turing_entity";
	public static final String DEFAULT_LANGUAGE = "en";
	public static final String URL = "url";

	private TurSNUtils() {
		throw new IllegalStateException("SN Utility class");
	}

	public static boolean hasCorrectedText(TurSESpellCheckResult turSESpellCheckResult) {
		return turSESpellCheckResult.isCorrected() && !StringUtils.isEmpty(turSESpellCheckResult.getCorrectedText());
	}

	public static boolean isAutoCorrectionEnabled(TurSNSiteSearchContext context, TurSNSite turSNSite) {
		return context.getTurSEParameters().getAutoCorrectionDisabled() != 1
				&& context.getTurSEParameters().getCurrentPage() == 1 && turSNSite.getSpellCheck() == 1
				&& turSNSite.getSpellCheckFixes() == 1;
	}

	public static URI requestToURI(HttpServletRequest request) {
		return UriComponentsBuilder.fromHttpRequest(new ServletServerHttpRequest(request)).build().toUri();
	}

	public static URI addOrReplaceParameter(URI uri, String paramName, String paramValue) {

		List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);

		StringBuilder sbQueryString = new StringBuilder();
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
		StringBuilder sbQueryString = new StringBuilder();
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
		StringBuilder sbQueryString = new StringBuilder();

		for (NameValuePair nameValuePair : params) {
			if (!(nameValuePair.getValue().equals(fq)
					&& nameValuePair.getName().equals(TurSNParamType.FILTER_QUERIES))) {
				resetPagination(sbQueryString, nameValuePair);
			}
		}

		return modifiedURI(uri, sbQueryString);
	}

	private static URI modifiedURI(URI uri, StringBuilder sbQueryString) {
		try {
			return new URI(uri.getRawPath() + "?" + removeAmpersand(sbQueryString));
		} catch (URISyntaxException e) {
			logger.error(e.getMessage(), e);
		}
		return uri;
	}

	private static String removeAmpersand(StringBuilder sbQueryString) {
		return sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);
	}

	private static void addParameterToQueryString(StringBuilder sbQueryString, String name, String value) {
		sbQueryString.append(String.format("%s=%s&", name, URLEncoder.encode(value, StandardCharsets.UTF_8)));
	}

	private static void resetPagination(StringBuilder sbQueryString, NameValuePair nameValuePair) {
		if ((nameValuePair.getName().equals(TurSNParamType.PAGE))) {
			addParameterToQueryString(sbQueryString, nameValuePair.getName(), "1");
		} else {
			addParameterToQueryString(sbQueryString, nameValuePair.getName(), nameValuePair.getValue());
		}
	}

	public static void addSNDocument(URI uri, Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, TurSNSiteFieldExt> facetMap, List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean,
			TurSEResult result, boolean isElevate) {
		addSNDocumentWithPostion(uri, fieldExtMap, facetMap, turSNSiteSearchDocumentsBean, result, isElevate, null);
	}

	public static void addSNDocumentWithPostion(URI uri, Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, TurSNSiteFieldExt> facetMap, List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean,
			TurSEResult result, boolean isElevate, Integer position) {
		TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean = new TurSNSiteSearchDocumentBean();
		Map<String, Object> turSEResultAttr = result.getFields();
		Set<String> attribs = turSEResultAttr.keySet();

		turSNSiteSearchDocumentBean.setElevate(isElevate);

		List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = addMetadataFromDocument(uri,
				facetMap, turSEResultAttr);

		turSNSiteSearchDocumentBean.setMetadata(turSNSiteSearchDocumentMetadataBeans);

		setSourcefromDocument(turSNSiteSearchDocumentBean, turSEResultAttr);

		Map<String, Object> fields = addFieldsFromDocument(fieldExtMap, turSEResultAttr, attribs);
		turSNSiteSearchDocumentBean.setFields(fields);
		if (position == null) {
			turSNSiteSearchDocumentsBean.add(turSNSiteSearchDocumentBean);
		} else {
			turSNSiteSearchDocumentsBean.add(position, turSNSiteSearchDocumentBean);
		}
	}

	private static Map<String, Object> addFieldsFromDocument(Map<String, TurSNSiteFieldExt> fieldExtMap,
			Map<String, Object> turSEResultAttr, Set<String> attribs) {
		Map<String, Object> fields = new HashMap<>();
		attribs.forEach(attribute -> {
			if (!attribute.startsWith(TURING_ENTITY)) {
				addFieldandValueToMap(turSEResultAttr, fields, attribute, getFieldName(fieldExtMap, attribute));
			}
		});
		return fields;
	}

	private static String getFieldName(Map<String, TurSNSiteFieldExt> fieldExtMap, String attribute) {
		String nodeName;
		if (fieldExtMap.containsKey(attribute)) {
			TurSNSiteFieldExt turSNSiteFieldExt = fieldExtMap.get(attribute);
			nodeName = turSNSiteFieldExt.getName();
		} else {
			nodeName = attribute;
		}
		return nodeName;
	}

	private static void addFieldandValueToMap(Map<String, Object> turSEResultAttr, Map<String, Object> fields,
			String attribute, String nodeName) {
		if (nodeName != null && fields.containsKey(nodeName)) {
			addValueToExistingFieldMap(turSEResultAttr, fields, attribute, nodeName);
		} else {
			fields.put(nodeName, turSEResultAttr.get(attribute));

		}
	}

	@SuppressWarnings("unchecked")
	private static void addValueToExistingFieldMap(Map<String, Object> turSEResultAttr, Map<String, Object> fields,
			String attribute, String nodeName) {
		if (!(fields.get(nodeName) instanceof List)) {
			List<Object> attributeValues = new ArrayList<>();
			attributeValues.add(fields.get(nodeName));
			attributeValues.add(turSEResultAttr.get(attribute));
			fields.put(nodeName, attributeValues);
		} else {
			((List<Object>) fields.get(nodeName)).add(turSEResultAttr.get(attribute));
		}
	}

	private static void setSourcefromDocument(TurSNSiteSearchDocumentBean turSNSiteSearchDocumentBean,
			Map<String, Object> turSEResultAttr) {
		if (turSEResultAttr.containsKey(URL)) {
			turSNSiteSearchDocumentBean.setSource((String) turSEResultAttr.get(URL));
		}
	}

	private static List<TurSNSiteSearchDocumentMetadataBean> addMetadataFromDocument(URI uri,
			Map<String, TurSNSiteFieldExt> facetMap, Map<String, Object> turSEResultAttr) {
		List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans = new ArrayList<>();
		facetMap.keySet().forEach(facet -> {
			if (turSEResultAttr.containsKey(facet)) {
				if (turSEResultAttr.get(facet) instanceof ArrayList) {
					((ArrayList<?>) turSEResultAttr.get(facet)).forEach(facetValueObject -> addFilterQueryByType(uri,
							turSNSiteSearchDocumentMetadataBeans, facet, facetValueObject));
				} else {
					addFilterQueryByType(uri, turSNSiteSearchDocumentMetadataBeans, facet, turSEResultAttr.get(facet));
				}

			}
		});

		return turSNSiteSearchDocumentMetadataBeans;
	}

	private static void addFilterQueryByType(URI uri,
			List<TurSNSiteSearchDocumentMetadataBean> turSNSiteSearchDocumentMetadataBeans, String facet,
			Object attrValue) {
		String facetValue = TurSolrField.convertFieldToString(attrValue);
		TurSNSiteSearchDocumentMetadataBean turSNSiteSearchDocumentMetadataBean = new TurSNSiteSearchDocumentMetadataBean();
		turSNSiteSearchDocumentMetadataBean
				.setHref(TurSNUtils.addFilterQuery(uri, facet + ":" + facetValue).toString());
		turSNSiteSearchDocumentMetadataBean.setText(facetValue);
		turSNSiteSearchDocumentMetadataBeans.add(turSNSiteSearchDocumentMetadataBean);
	}
}
