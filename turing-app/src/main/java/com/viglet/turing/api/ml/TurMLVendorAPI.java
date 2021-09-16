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

package com.viglet.turing.api.ml;

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

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/vendor")
@Api(tags = "Machine Learning Vendor", description = "Machine Learning Vendor API")
public class TurMLVendorAPI {

	@Autowired
	TurMLVendorRepository turMLVendorRepository;

	@ApiOperation(value = "Machine Learning Vendor List")
	@GetMapping
	public List<TurMLVendor> turMLVendorList() {
		return this.turMLVendorRepository.findAll();
	}

	@ApiOperation(value = "Show a Machine Learning Vendor")
	@GetMapping("/{id}")
	public TurMLVendor turMLVendorGet(@PathVariable String id) {
		return this.turMLVendorRepository.findById(id).orElse(new TurMLVendor());
	}

	@ApiOperation(value = "Update a Machine Learning Vendor")
	@PutMapping("/{id}")
	public TurMLVendor turMLVendorUpdate(@PathVariable String id, @RequestBody TurMLVendor turMLVendor) {
		return this.turMLVendorRepository.findById(id).map(turMLVendorEdit -> {
			turMLVendorEdit.setDescription(turMLVendor.getDescription());
			turMLVendorEdit.setPlugin(turMLVendor.getPlugin());
			turMLVendorEdit.setTitle(turMLVendor.getTitle());
			turMLVendorEdit.setWebsite(turMLVendor.getWebsite());
			this.turMLVendorRepository.save(turMLVendorEdit);
			return turMLVendorEdit;
		}).orElse(new TurMLVendor());

	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Vendor")
	@DeleteMapping("/{id}")
	public boolean turMLVendorDelete(@PathVariable String id) {
		this.turMLVendorRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Vendor")
	@PostMapping
	public TurMLVendor turMLVendorAdd(@RequestBody TurMLVendor turMLVendor) {
		this.turMLVendorRepository.save(turMLVendor);
		return turMLVendor;

	}
}
