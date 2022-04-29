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

package com.viglet.turing.api.sn.search;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.sn.ac.TurSNAutoComplete;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{siteName}/ac")
@Tag(name = "Semantic Navigation Auto Complete", description = "Semantic Navigation Auto Complete API")
public class TurSNSiteAutoCompleteAPI {
	@Autowired
	private TurSNAutoComplete turSNAutoComplete;

	@GetMapping
	public List<String> turSNSiteAutoComplete(@PathVariable String siteName,
			@RequestParam(required = true, name = TurSNParamType.QUERY) String q,
			@RequestParam(required = false, defaultValue = "20", name = TurSNParamType.ROWS) long rows,
			@RequestParam(required = false, name = TurSNParamType.LOCALE) String locale, HttpServletRequest request) {
		return turSNAutoComplete.autoComplete(siteName, q, locale, rows);
	}
}
