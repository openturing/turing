package com.viglet.turing.api.sn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrField;

import io.swagger.annotations.Api;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.TurSEStopword;

@RestController
@RequestMapping("/api/sn/{siteName}/ac")
@Api(tags = "Semantic Navigation Auto Complete", description = "Semantic Navigation Auto Complete API")
public class TurSNSiteAutoCompleteAPI {

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
			@RequestParam(required = false, name = "q") String q, HttpServletRequest request)
			throws JSONException, IOException {
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		SpellCheckResponse turSEResults = null;
		turSolr.init(turSNSite);
		try {
			turSEResults = turSolr.autoComplete(q);
		} catch (Exception e) {
			e.printStackTrace();
		}
		List<String> termList = turSEResults.getSuggestions().get(0).getAlternatives();
		List<String> stopWords = turSEStopword.getStopWords("pt");
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

		List<String> termListShrink = new ArrayList<String>();
		previousTerm = null;
		int i = 0;
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
			i = i++;
		}
		return termListShrink;
	}
}
