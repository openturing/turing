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

package com.viglet.turing.api.entity;

import java.util.List;

import org.json.JSONException;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/entity")
@Api(tags = "Entity", description = "Entity API")
public class TurNLPEntityAPI {
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;

	@ApiOperation(value = "Entity List")
	@GetMapping
	public List<TurNLPEntity> turNLPEntityList() throws JSONException {
		return this.turNLPEntityRepository.findAll();
	}

	@ApiOperation(value = "Local Entity list")
	@GetMapping("/local")
	public List<TurNLPEntity> turNLPEntityLocal() throws JSONException {
		return this.turNLPEntityRepository.findByLocal(1);
	}
	
	@ApiOperation(value = "Show a entity")
	@GetMapping("/{id}")
	public TurNLPEntity turNLPEntityGet(@PathVariable String id) throws JSONException {
		return this.turNLPEntityRepository.findById(id).get();
	}

	@ApiOperation(value = "Update a entity")
	@PutMapping("/{id}")
	public TurNLPEntity turNLPEntityUpdate(@PathVariable String id, @RequestBody TurNLPEntity turNLPEntity) throws Exception {
		TurNLPEntity turNLPEntityEdit =  this.turNLPEntityRepository.findById(id).get();
		turNLPEntityEdit.setName(turNLPEntity.getName());
		turNLPEntityEdit.setDescription(turNLPEntity.getDescription());
		this.turNLPEntityRepository.save(turNLPEntityEdit);
		return turNLPEntityEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a entity")
	@DeleteMapping("/{id}")
	public boolean turNLPEntityDelete(@PathVariable String id) {
		TurNLPEntity turNLPEntity =  this.turNLPEntityRepository.findById(id).get();
		this.turNLPEntityRepository.delete(turNLPEntity);
		return true;
	}

	@ApiOperation(value = "Create a entity")
	@PostMapping
	public TurNLPEntity turNLPEntityAdd(@RequestBody TurNLPEntity turNLPEntity) throws Exception {
		this.turNLPEntityRepository.save(turNLPEntity);
		return turNLPEntity;

	}
}