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

package com.viglet.turing.api.sn.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.client.solrj.response.SpellCheckResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstance;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.TurSEStopword;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{siteName}/locale/ac")
@Tag(name = "Semantic Navigation Auto Complete", description = "Semantic Navigation Auto Complete API")
public class TurSNSiteAutoCompleteAPI {
	private static final Log logger = LogFactory.getLog(TurSNSiteAutoCompleteAPI.class);
	@Autowired
	private TurSolr turSolr;
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSEStopword turSEStopword;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;
	
	@GetMapping
	public List<String> turSNSiteAutoComplete(@PathVariable String siteName,
			@PathVariable String locale,
			@RequestParam(required = true, name = TurSNParamType.QUERY) String q,
			@RequestParam(required = false, defaultValue = "20", name = TurSNParamType.ROWS) long rows, 
			HttpServletRequest request) {

		List<String> termListShrink = new ArrayList<>();
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		SpellCheckResponse turSEResults = null;
		TurSolrInstance turSolrInstance = turSolrInstanceProcess.initSolrInstance(turSNSite, locale);
		try {
			turSEResults = turSolr.autoComplete(turSolrInstance, q);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		List<String> termList = turSEResults.getSuggestions().get(0).getAlternatives();
		List<String> stopWords;
		try {
			stopWords = turSEStopword.getStopWords("pt");

			List<String> termListFormatted = new ArrayList<>();
			int tokenQSize = q.split(" ").length;
			String previousTerm = null;
			boolean previousFinishedStopWords = false;
			for (String term : termList) {
				String[] token = term.split(" ");
				String lastToken = token[token.length - 1];
				if (token.length > tokenQSize) {
					if (previousTerm == null) {
						if (!stopWords.contains(lastToken) || !term.contains(previousTerm)) {
							termListFormatted.add(term);
							previousFinishedStopWords = false;
						} else {
							previousFinishedStopWords = true;
						}
						previousTerm = term;
					} else {
						if (previousFinishedStopWords) {
							if (!stopWords.contains(lastToken)) {
								termListFormatted.add(term);
								previousFinishedStopWords = false;
							}
						} else {
							previousFinishedStopWords = true;
						}
					}

				} else {
					termListFormatted.add(term);
					previousTerm = term + "";
				}
			}

			previousTerm = null;

			for (String term : termListFormatted) {
				int currentIndex = termListFormatted.indexOf(term);
				String[] token = term.split(" ");
				if ((token.length <= tokenQSize + 1) || previousTerm == null) {
					termListShrink.add(term);
					previousTerm = term;
				} else {
					if (!term.contains(previousTerm)) {
						termListShrink.add(term);
					} else {
						if ((termListFormatted.size() > currentIndex + 1)
								&& (!termListFormatted.get(currentIndex + 1).contains(term))) {
							termListShrink.add(term);
						}
					}
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return termListShrink.stream().limit(rows).collect(Collectors.toList());
	}
}
