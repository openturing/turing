/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.api.se;

import com.google.inject.Inject;
import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/se")
@Tag(name = "Search Engine", description = "Search Engine API")
public class TurSEInstanceAPI {
	private final TurSEInstanceRepository turSEInstanceRepository;
	private final TurSolrInstanceProcess turSolrInstanceProcess;
	private final TurSolr turSolr;

	@Inject
	public TurSEInstanceAPI(TurSEInstanceRepository turSEInstanceRepository,
							TurSolrInstanceProcess turSolrInstanceProcess,
							TurSolr turSolr) {
		this.turSEInstanceRepository = turSEInstanceRepository;
		this.turSolrInstanceProcess = turSolrInstanceProcess;
		this.turSolr = turSolr;
	}

	@Operation(summary = "Search Engine List")
	@GetMapping
	public List<TurSEInstance> turSEInstanceList() {
		return this.turSEInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
	}

	@Operation(summary = "Search Engine structure")
	@GetMapping("/structure")
	public TurSEInstance turNLPInstanceStructure() {
		TurSEInstance turSEInstance = new TurSEInstance();
		turSEInstance.setTurSEVendor(new TurSEVendor());
		return turSEInstance;

	}

	@Operation(summary = "Show a Search Engine")
	@GetMapping("/{id}")
	public TurSEInstance turSEInstanceGet(@PathVariable String id) {
		return this.turSEInstanceRepository.findById(id).orElse(new TurSEInstance());
	}

	@Operation(summary = "Update a Search Engine")
	@PutMapping("/{id}")
	public TurSEInstance turSEInstanceUpdate(@PathVariable String id, @RequestBody TurSEInstance turSEInstance) {
		return turSEInstanceRepository.findById(id).map(turSEInstanceEdit -> {
			turSEInstanceEdit.setTitle(turSEInstance.getTitle());
			turSEInstanceEdit.setDescription(turSEInstance.getDescription());
			turSEInstanceEdit.setTurSEVendor(turSEInstance.getTurSEVendor());
			turSEInstanceEdit.setHost(turSEInstance.getHost());
			turSEInstanceEdit.setPort(turSEInstance.getPort());
			turSEInstanceEdit.setEnabled(turSEInstance.getEnabled());
			this.turSEInstanceRepository.save(turSEInstanceEdit);
			return turSEInstanceEdit;
		}).orElse(new TurSEInstance());

	}

	@Transactional
	@Operation(summary = "Delete a Search Engine")
	@DeleteMapping("/{id}")
	public boolean turSEInstanceDelete(@PathVariable String id) {
		this.turSEInstanceRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Search Engine")
	@PostMapping
	public TurSEInstance turSEInstanceAdd(@RequestBody TurSEInstance turSEInstance) {
		this.turSEInstanceRepository.save(turSEInstance);
		return turSEInstance;

	}

	@GetMapping("/select")
	public ResponseEntity<TurSEResults> turSEInstanceSelect(@RequestParam(required = false, name = TurSNParamType.QUERY) String q,
															@RequestParam(required = false, name = TurSNParamType.PAGE) Integer currentPage,
															@RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_DEFAULT) List<String> filterQueriesDefault,
															@RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_AND) List<String> filterQueriesAnd,
															@RequestParam(required = false, name = TurSNParamType.FILTER_QUERIES_OR) List<String> filterQueriesOr,
															@RequestParam(required = false, name = TurSNParamType.FILTER_QUERY_OPERATOR, defaultValue = "NONE")
																TurSNFilterQueryOperator fqOperator,
															@RequestParam(required = false, name = TurSNParamType.SORT) String sort,
															@RequestParam(required = false, name = TurSNParamType.ROWS) Integer rows,
															@RequestParam(required = false, name = TurSNParamType.GROUP) String group) {

		currentPage = (currentPage == null || currentPage <= 0) ? 1 : currentPage;
		rows = rows == null ? 0 : rows;
		TurSEParameters turSEParameters = new TurSEParameters(q, new TurSEFilterQueryParameters(filterQueriesDefault, filterQueriesAnd, filterQueriesOr, fqOperator), currentPage,
				sort, rows, group, 0);
		return turSolrInstanceProcess.initSolrInstance()
				.map(turSolrInstance -> new ResponseEntity<>(turSolr.retrieveSolr(turSolrInstance, turSEParameters,
						"text"), HttpStatus.OK))
				.orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

}
