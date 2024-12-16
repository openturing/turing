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

package com.viglet.turing.api.ml.data.group;

import java.util.ArrayList;
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
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.repository.storage.TurDataGroupCategoryRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/category")
@Tag(name ="Machine Learning Category by Group", description = "Machine Learning Category by Group API")
public class TurMLDataGroupCategoryAPI {

	@Autowired
	private TurDataGroupRepository turDataGroupRepository;
	@Autowired
	private TurDataGroupCategoryRepository turDataGroupCategoryRepository;

	@Operation(summary = "Machine Learning Data Group Category List")
	@GetMapping
	public List<TurDataGroupCategory> turDataGroupCategoryList(@PathVariable int dataGroupId) {
		return turDataGroupRepository.findById(dataGroupId).map(this.turDataGroupCategoryRepository::findByTurDataGroup)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Machine Learning Data Group Category")
	@GetMapping("/{id}")
	public TurDataGroupCategory turDataGroupCategoryGet(@PathVariable int dataGroupId, @PathVariable int id) {
		return this.turDataGroupCategoryRepository.findById(id).orElse(new TurDataGroupCategory());
	}

	@Operation(summary = "Update a Machine Learning Data Group Category")
	@PutMapping("/{id}")
	public TurDataGroupCategory turDataGroupCategoryUpdate(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurMLCategory turMLCategory) {
		return this.turDataGroupCategoryRepository.findById(id).map(turDataGroupCategoryEdit -> {
			turDataGroupCategoryEdit.setTurMLCategory(turMLCategory);
			this.turDataGroupCategoryRepository.save(turDataGroupCategoryEdit);
			return turDataGroupCategoryEdit;
		}).orElse(new TurDataGroupCategory());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Group Category")
	@DeleteMapping("/{id}")
	public boolean turDataGroupCategoryDelete(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupCategoryRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Group Category")
	@PostMapping
	public TurDataGroupCategory turDataGroupCategoryAdd(@PathVariable int dataGroupId,
			@RequestBody TurDataGroupCategory turDataGroupCategory) {
		return turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			turDataGroupCategory.setTurDataGroup(turDataGroup);
			this.turDataGroupCategoryRepository.save(turDataGroupCategory);
			return turDataGroupCategory;
		}).orElse(new TurDataGroupCategory());

	}
}
