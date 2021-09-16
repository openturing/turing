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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/se/vendor")
@Api(tags = "Search Engine Vendor", description = "Search Engine Vendor API")
public class TurSEVendorAPI {

	@Autowired
	TurSEVendorRepository turSEVendorRepository;

	@ApiOperation(value = "Search Engine Vendor List")
	@GetMapping
	public List<TurSEVendor> turSEVendorList() {
		return this.turSEVendorRepository.findAll();
	}

	@ApiOperation(value = "Show a Search Engine Vendor")
	@GetMapping("/{id}")
	public TurSEVendor turSEVendorGet(@PathVariable String id) {
		return this.turSEVendorRepository.findById(id).orElse(new TurSEVendor());
	}

	@ApiOperation(value = "Update a Search Engine Vendor")
	@PutMapping("/{id}")
	public TurSEVendor turSEVendorUpdate(@PathVariable String id, @RequestBody TurSEVendor turSEVendor) {
		return this.turSEVendorRepository.findById(id).map(turSEVendorEdit -> {
			turSEVendorEdit.setDescription(turSEVendor.getDescription());
			turSEVendorEdit.setPlugin(turSEVendor.getPlugin());
			turSEVendorEdit.setTitle(turSEVendor.getTitle());
			turSEVendorEdit.setWebsite(turSEVendor.getWebsite());
			this.turSEVendorRepository.save(turSEVendorEdit);
			return turSEVendorEdit;
		}).orElse(new TurSEVendor());

	}

	@Transactional
	@ApiOperation(value = "Delete a Search Engine Vendor")
	@DeleteMapping("/{id}")
	public boolean turSEVendorDelete(@PathVariable String id) {
		this.turSEVendorRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Search Engine Vendor")
	@PostMapping
	public TurSEVendor turSEVendorAdd(@RequestBody TurSEVendor turSEVendor) {
		this.turSEVendorRepository.save(turSEVendor);
		return turSEVendor;

	}
}
