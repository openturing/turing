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

package com.viglet.turing.api.se;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.solr.TurSolr;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/se")
@Api(tags = "Search Engine", description = "Search Engine API")
public class TurSEInstanceAPI {
	private static final Log logger = LogFactory.getLog(TurSEInstanceAPI.class);
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;

	@ApiOperation(value = "Search Engine List")
	@GetMapping
	public List<TurSEInstance> turSEInstanceList() {
		return this.turSEInstanceRepository.findAll();
	}

	@ApiOperation(value = "Search Engine structure")
	@GetMapping("/structure")
	public TurSEInstance turNLPInstanceStructure() {
		TurSEInstance turSEInstance = new TurSEInstance();
		turSEInstance.setTurSEVendor(new TurSEVendor());
		turSEInstance.setLanguage("en_US");
		return turSEInstance;

	}

	@ApiOperation(value = "Show a Search Engine")
	@GetMapping("/{id}")
	public TurSEInstance turSEInstanceGet(@PathVariable String id) {
		return this.turSEInstanceRepository.findById(id).orElse(new TurSEInstance());
	}

	@ApiOperation(value = "Update a Search Engine")
	@PutMapping("/{id}")
	public TurSEInstance turSEInstanceUpdate(@PathVariable String id, @RequestBody TurSEInstance turSEInstance)
			throws Exception {
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
	@ApiOperation(value = "Delete a Search Engine")
	@DeleteMapping("/{id}")
	public boolean turSEInstanceDelete(@PathVariable String id) {
		this.turSEInstanceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Search Engine")
	@PostMapping
	public TurSEInstance turSEInstanceAdd(@RequestBody TurSEInstance turSEInstance) {
		this.turSEInstanceRepository.save(turSEInstance);
		return turSEInstance;

	}

	@GetMapping("/select")
	public String turSEInstanceSelect(@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "p") Integer currentPage,
			@RequestParam(required = false, name = "fq[]") List<String> fq,
			@RequestParam(required = false, name = "tr[]") List<String> tr,
			@RequestParam(required = false, name = "sort") String sort,
			@RequestParam(required = false, name = "rows") Integer rows) {

		if (currentPage == null || currentPage <= 0)
			currentPage = 1;

		if (rows == null)
			rows = 0;

		String result = null;
		TurSolr turSolr = new TurSolr();
		try {
			result = turSolr.retrieveSolr(q, fq, tr, currentPage, sort, rows).toString();

		} catch (Exception e) {
			logger.error(e);
		}
		return result;
	}

}