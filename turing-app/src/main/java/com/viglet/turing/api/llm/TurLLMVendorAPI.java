/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.api.llm;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm/vendor")
@Tag(name = "Large Language Model Vendor", description = "Large Language Model Vendor API")
public class TurLLMVendorAPI {
	private final TurLLMVendorRepository turLLMVendorRepository;

	@Inject
	public TurLLMVendorAPI(TurLLMVendorRepository turLLMVendorRepository) {
		this.turLLMVendorRepository = turLLMVendorRepository;
	}

	@Operation(summary = "Large Language Model Vendor List")
	@GetMapping
	public List<TurLLMVendor> turLLMVendorList() {
		return this.turLLMVendorRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
	}

	@Operation(summary = "Show a Large Language Model Vendor")
	@GetMapping("/{id}")
	public TurLLMVendor turLLMVendorGet(@PathVariable String id) {
		return this.turLLMVendorRepository.findById(id).orElse(new TurLLMVendor());
	}

	@Operation(summary = "Update a Large Language Model Vendor")
	@PutMapping("/{id}")
	public TurLLMVendor turLLMVendorUpdate(@PathVariable String id, @RequestBody TurLLMVendor turLLMVendor) {
		return this.turLLMVendorRepository.findById(id).map(turLLMVendorEdit -> {
			turLLMVendorEdit.setDescription(turLLMVendor.getDescription());
			turLLMVendorEdit.setPlugin(turLLMVendor.getPlugin());
			turLLMVendorEdit.setTitle(turLLMVendor.getTitle());
			turLLMVendorEdit.setWebsite(turLLMVendor.getWebsite());
			this.turLLMVendorRepository.save(turLLMVendorEdit);
			return turLLMVendorEdit;
		}).orElse(new TurLLMVendor());

	}

	@Transactional
	@Operation(summary = "Delete a Large Language Model Vendor")
	@DeleteMapping("/{id}")
	public boolean turLLMVendorDelete(@PathVariable String id) {
		this.turLLMVendorRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Large Language Model Vendor")
	@PostMapping
	public TurLLMVendor turLLMVendorAdd(@RequestBody TurLLMVendor turLLMVendor) {
		this.turLLMVendorRepository.save(turLLMVendor);
		return turLLMVendor;

	}
}
