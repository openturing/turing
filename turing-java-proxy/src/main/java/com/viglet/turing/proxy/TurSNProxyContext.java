/*
 * Copyright (C) 2016-2021 the original author or authors. 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.proxy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 * 
 */
@RestController
@RequestMapping("/__tur/sn/{siteName}")
public class TurSNProxyContext {
	private static final Log logger = LogFactory.getLog(TurSNProxyContext.class);
	@Value("${turing.endpoint}")
	private String turingEndpoint;

	private final static String API_ENDPOINT_FORMAT = "%s/api/sn/%s/%s";
	private final static String API_ENDPOINT_SEARCH = "search";
	private final static String API_ENDPOINT_AUTO_COMPLETE = "ac";
	private final static String PARAM_Q = "q";
	private final static String PARAM_P = "p";
	private final static String PARAM_ROWS = "rows";
	private final static String PARAM_FQ = "fq[]";
	private final static String PARAM_TR = "tr[]";
	private final static String PARAM_SORT = "SORT";

	@GetMapping("/search")
	public ResponseEntity<Object> turSNSiteSearchSelect(HttpServletRequest request, @PathVariable String siteName,
														@RequestParam(required = false, name = PARAM_Q) String q,
														@RequestParam(required = false, name = PARAM_P) String p,
														@RequestParam(required = false, name = PARAM_ROWS) String rows,
														@RequestParam(required = false, name = PARAM_FQ) String[] fq,
														@RequestParam(required = false, name = PARAM_TR) String[] tr,
														@RequestParam(required = false, name = PARAM_SORT) String sort) {

		try {
			URIBuilder turingURL = new URIBuilder(
					String.format(API_ENDPOINT_FORMAT, turingEndpoint, siteName, API_ENDPOINT_SEARCH));
			addStringParameter(turingURL, PARAM_Q, q);
			addStringParameter(turingURL, PARAM_P, p);
			addStringParameter(turingURL, PARAM_SORT, sort);
			addStringParameter(turingURL, PARAM_ROWS, rows);
			addArrayParameter(turingURL, PARAM_FQ, fq);
			addArrayParameter(turingURL, PARAM_TR, tr);

			return responseTuring(turingURL);

		} catch (URISyntaxException e) {
			logger.error(e.getMessage(), e);

		}
		return responseNotFound();
	}

	private void addArrayParameter(URIBuilder turingURL, String parameter, String[] values) {
		if (ArrayUtils.isNotEmpty(values))
			Arrays.asList(values).forEach(value -> turingURL.addParameter(parameter, value));
	}

	private void addStringParameter(URIBuilder turingURL, String parameter, String value) {
		if (StringUtils.isNotEmpty(value))
			turingURL.addParameter(parameter, value);
	}

	@GetMapping("/ac")
	public ResponseEntity<Object> turSNSiteAutoComplete(HttpServletRequest request, @PathVariable String siteName,
			@RequestParam(required = true, name = PARAM_Q) String q,
			@RequestParam(required = false, name = PARAM_ROWS) String rows) {

		try {
			URIBuilder turingURL = new URIBuilder(
					String.format(API_ENDPOINT_FORMAT, turingEndpoint, siteName, API_ENDPOINT_AUTO_COMPLETE));
			addStringParameter(turingURL, PARAM_Q, q);
			addStringParameter(turingURL, PARAM_ROWS, rows);

			return responseTuring(turingURL);
		} catch (URISyntaxException e) {
			logger.error(e.getMessage(), e);
		}

		return responseNotFound();
	}

	private ResponseEntity<Object> responseNotFound() {
		return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
	}

	private ResponseEntity<Object> responseTuring(URIBuilder turingURL) throws URISyntaxException {
		final HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_JSON);
		return new ResponseEntity<>(this.getResults(turingURL), httpHeaders, HttpStatus.OK);
	}

	private String getResults(URIBuilder url) {
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			return EntityUtils.toString(client.execute(new HttpGet(url.build().toString())).getEntity(),
					StandardCharsets.UTF_8);
		} catch (IOException | ParseException | URISyntaxException e) {
			logger.error(e.getMessage(), e);
		}
		return StringUtils.EMPTY;
	}
}
