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

package com.viglet.turing.api.ml.category;

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

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/category")
@Tag(name ="Category", description = "Category API")
public class TurMLCategoryAPI {

	@Autowired
	private TurMLCategoryRepository turMLCategoryRepository;

	@Operation(summary = "Machine Learning Category List")
	@GetMapping
	public List<TurMLCategory> turMLCategoryList() {
		return this.turMLCategoryRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Category")
	@GetMapping("/{id}")
	public TurMLCategory turMLCategoryGet(@PathVariable int id) {
		return this.turMLCategoryRepository.findById(id).orElse(new TurMLCategory());
	}

	@Operation(summary = "Update a Machine Learning Category")
	@PutMapping("/{id}")
	public TurMLCategory turMLCategoryUpdate(@PathVariable int id, @RequestBody TurMLCategory turMLCategory) {
		return this.turMLCategoryRepository.findById(id).map(turMLCategoryEdit -> {
			turMLCategoryEdit.setInternalName(turMLCategory.getInternalName());
			turMLCategoryEdit.setName(turMLCategory.getName());
			turMLCategoryEdit.setDescription(turMLCategory.getDescription());
			this.turMLCategoryRepository.save(turMLCategoryEdit);

			return turMLCategoryEdit;
		}).orElse(new TurMLCategory());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Category")
	@DeleteMapping("/{id}")
	public boolean turMLCategoryDelete(@PathVariable int id) {
		this.turMLCategoryRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Category")
	@PostMapping
	public TurMLCategory turMLCategoryAdd(@RequestBody TurMLCategory turMLCategory) {
		this.turMLCategoryRepository.save(turMLCategory);
		return turMLCategory;

	}
}
