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

package com.viglet.turing.api.entity;

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

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/entity")
@Tag(name ="Entity", description = "Entity API")
public class TurNLPEntityAPI {
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;

	@Operation(summary = "Entity List")
	@GetMapping
	public List<TurNLPEntity> turNLPEntityList() {
		return this.turNLPEntityRepository.findAll();
	}

	@Operation(summary = "Entity structure")
	@GetMapping("/structure")
	public TurNLPEntity turNLPEntityStructure() {
		return new TurNLPEntity();

	}

	@Operation(summary = "Local Entity list")
	@GetMapping("/local")
	public List<TurNLPEntity> turNLPEntityLocal() {
		return this.turNLPEntityRepository.findByLocal(1);
	}

	@Operation(summary = "Show a entity")
	@GetMapping("/{id}")
	public TurNLPEntity turNLPEntityGet(@PathVariable String id) {
		return this.turNLPEntityRepository.findById(id).orElse(new TurNLPEntity());
	}

	@Operation(summary = "Update a entity")
	@PutMapping("/{id}")
	public TurNLPEntity turNLPEntityUpdate(@PathVariable String id, @RequestBody TurNLPEntity turNLPEntity) {
		return this.turNLPEntityRepository.findById(id).map(turNLPEntityEdit -> {
			turNLPEntityEdit.setName(turNLPEntity.getName());
			turNLPEntityEdit.setDescription(turNLPEntity.getDescription());
			this.turNLPEntityRepository.save(turNLPEntityEdit);
			return turNLPEntityEdit;
		}).orElse(new TurNLPEntity());

	}

	@Transactional
	@Operation(summary = "Delete a entity")
	@DeleteMapping("/{id}")
	public boolean turNLPEntityDelete(@PathVariable String id) {
		return this.turNLPEntityRepository.findById(id).map(turNLPEntity -> {
			this.turNLPEntityRepository.delete(turNLPEntity);
			return true;
		}).orElse(false);

	}

	@Operation(summary = "Create a entity")
	@PostMapping
	public TurNLPEntity turNLPEntityAdd(@RequestBody TurNLPEntity turNLPEntity) {
		this.turNLPEntityRepository.save(turNLPEntity);
		return turNLPEntity;

	}
}