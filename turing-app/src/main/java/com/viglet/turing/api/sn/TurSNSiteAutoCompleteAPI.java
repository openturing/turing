/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.api.sn;

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
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.TurSEStopword;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/sn/{siteName}/ac")
@Api(tags = "Semantic Navigation Auto Complete", description = "Semantic Navigation Auto Complete API")
public class TurSNSiteAutoCompleteAPI {
	private static final Log logger = LogFactory.getLog(TurSNSiteAutoCompleteAPI.class);
	@Autowired
	TurSolr turSolr;
	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurSolrField turSolrField;
	@Autowired
	TurSEStopword turSEStopword;

	@GetMapping
	public List<String> turSNSiteAutoComplete(@PathVariable String siteName,
			@RequestParam(required = true, name = "q") String q,
			@RequestParam(required = false, defaultValue = "20", name = "rows") long rows, HttpServletRequest request) {

		List<String> termListShrink = new ArrayList<>();
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		SpellCheckResponse turSEResults = null;
		turSolr.init(turSNSite);
		try {
			turSEResults = turSolr.autoComplete(q);
		} catch (Exception e) {
			e.printStackTrace();
		}
		List<String> termList = turSEResults.getSuggestions().get(0).getAlternatives();
		List<String> stopWords;
		try {
			stopWords = turSEStopword.getStopWords("pt");

			List<String> termListFormatted = new ArrayList<String>();
			int tokenQSize = q.split(" ").length;
			String previousTerm = null;
			boolean previousFinishedStopWords = false;
			for (String term : termList) {
				String[] token = term.split(" ");
				String lastToken = token[token.length - 1];
				if (token.length > tokenQSize) {
					if (previousTerm == null) {
						if (!stopWords.contains(lastToken)) {
							termListFormatted.add(term);
							previousFinishedStopWords = false;
						} else {
							previousFinishedStopWords = true;
						}
						previousTerm = term;

					} else if (!term.contains(previousTerm)) {
						if (!stopWords.contains(lastToken)) {
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
			logger.error(e);
		}
		return termListShrink.stream().limit(rows).collect(Collectors.toList());
	}
}
