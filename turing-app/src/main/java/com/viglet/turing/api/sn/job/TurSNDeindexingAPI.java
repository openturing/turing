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

package com.viglet.turing.api.sn.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/sn/{id}/deindex")
@Tag(name = "Semantic Navigation Deindexing", description = "Semantic Navigation Deindexing API")
public class TurSNDeindexingAPI {
	static final Logger logger = LogManager.getLogger(TurSNDeindexingAPI.class.getName());
	@Autowired
	private TurSNImportAPI turSNImportAPI;
	
	public static final String INDEXING_QUEUE = "indexing.queue";

	@PostMapping
	public String turSNDesindexingBroker(@PathVariable String id, @RequestBody TurSNJobItems turSNJobItems)
			throws JSONException {
		TurSNJob turSNJob = new TurSNJob();
		turSNJob.setSiteId(id);
		turSNJob.setTurSNJobItems(turSNJobItems);
		turSNImportAPI.send(turSNJob);
		return "Ok";

	}
}
