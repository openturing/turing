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

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.sn.TurSNUtils;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{siteName}/{locale}/spell-check")
@Tag(name = "Semantic Navigation Spell Check", description = "Semantic Navigation Spell Check API")
public class TurSNSiteSpellCheckAPI {
	@Autowired
	private TurSolr turSolr;
	@Autowired
	private TurSolrInstanceProcess turSolrInstanceProcess;

	@GetMapping
	public TurSNSiteSpellCheckBean turSNSiteSpellCheck(@PathVariable String siteName, @PathVariable String locale,
			@RequestParam(required = true, name = TurSNParamType.QUERY) String q, HttpServletRequest request) {

		return turSolrInstanceProcess.initSolrInstance(siteName, locale).map(turSolrInstance -> {
			TurSNSiteSearchContext turSNSiteSearchContext = new TurSNSiteSearchContext(siteName,
					new TurSEParameters(q, null, 1, "relevance", 10, 0), locale,
					TurSNUtils.requestToURI(request));
			return new TurSNSiteSpellCheckBean(turSNSiteSearchContext, turSolr.spellCheckTerm(turSolrInstance, q));
		}).orElse(null);

	}
}
