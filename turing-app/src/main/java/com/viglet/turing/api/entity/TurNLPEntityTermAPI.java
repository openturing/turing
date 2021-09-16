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

import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/entity/terms")
@Api(tags = "Term", description = "Term API")
public class TurNLPEntityTermAPI {
	@Autowired
	private TurTermRepository turTermRepository;

	@ApiOperation(value = "Entity Term list")
	@GetMapping
	public List<TurTerm> turTermList() {
		return this.turTermRepository.findAll();
	}

	@ApiOperation(value = "Show a Entity Term")
	@GetMapping("/{id}")
	public TurTerm turTermGet(@PathVariable String id) {
		return this.turTermRepository.findById(id).orElse(new TurTerm());
	}

	@ApiOperation(value = "Update a Entity Term")
	@PutMapping("/{id}")
	public TurTerm turTermUpdate(@PathVariable String id, @RequestBody TurTerm turTerm) {
		return this.turTermRepository.findById(id).map(turTermEdit -> {
			turTermEdit.setName(turTerm.getName());
			turTermEdit.setIdCustom(turTerm.getIdCustom());
			this.turTermRepository.save(turTermEdit);
			return turTermEdit;
		}).orElse(new TurTerm());

	}

	@Transactional
	@ApiOperation(value = "Delete a Entity Term")
	@DeleteMapping("/{id}")
	public boolean turTermDelete(@PathVariable String id) {
		this.turTermRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Entity Term")
	@PostMapping
	public TurTerm turTermAdd(@RequestBody TurTerm turTerm) {
		this.turTermRepository.save(turTerm);
		return turTerm;

	}
}
