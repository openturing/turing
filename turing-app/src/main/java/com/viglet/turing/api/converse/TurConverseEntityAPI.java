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

package com.viglet.turing.api.converse;

import java.util.List;
import java.util.Set;

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

import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntityTerm;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityTermRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/entity")
@Api(tags = "Converse Entity", description = "Converse Entity API")
public class TurConverseEntityAPI {

	@Autowired
	private TurConverseEntityRepository turConverseEntityRepository;
	@Autowired
	private TurConverseEntityTermRepository turConverseEntityTermRepository;

	@ApiOperation(value = "Converse Entity List")
	@GetMapping
	public List<TurConverseEntity> turConverseEntityList() {
		return this.turConverseEntityRepository.findAll();
	}

	@ApiOperation(value = "Show a Converse Entity")
	@GetMapping("/{id}")
	public TurConverseEntity turConverseEntityGet(@PathVariable String id) {
		return this.turConverseEntityRepository.findById(id).map(turConverseEntity -> {
			turConverseEntity.setTerms(turConverseEntityTermRepository.findByEntity(turConverseEntity));
			return turConverseEntity;
		}).orElse(new TurConverseEntity());
	}

	@ApiOperation(value = "Create a Converse Entity")
	@PostMapping
	public TurConverseEntity turConverseEntityAdd(@RequestBody TurConverseEntity turConverseEntity) {
		return this.saveEntity(turConverseEntity);
	}

	@ApiOperation(value = "Update a Converse Entity")
	@PutMapping("/{id}")
	public TurConverseEntity turConverseEntityUpdate(@PathVariable String id,
			@RequestBody TurConverseEntity turConverseEntity) {
		return this.saveEntity(turConverseEntity);

	}

	private TurConverseEntity saveEntity(TurConverseEntity turConverseEntity) {
		turConverseEntityRepository.save(turConverseEntity);

		Set<TurConverseEntityTerm> terms = turConverseEntity.getTerms();
		for (TurConverseEntityTerm term : terms) {
			if (term != null) {
				term.setEntity(turConverseEntity);
				turConverseEntityTermRepository.save(term);
			}
		}
		return turConverseEntity;
	}

	@Transactional
	@ApiOperation(value = "Delete a Converse Entity")
	@DeleteMapping("/{id}")
	public boolean turConverseEntityDelete(@PathVariable String id) {
		this.turConverseEntityRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Converse Entity Model")
	@GetMapping("/model")
	public TurConverseEntity turConverseEntityModel() {
		TurConverseEntity turConverseEntity = new TurConverseEntity();
		return turConverseEntity;
	}
}
