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

package com.viglet.turing.api.ml.data.group;

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

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/data/group")
@Api(tags = "Machine Learning Group", description = "Machine Learning Group API")
public class TurMLDataGroupAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;

	@ApiOperation(value = "Machine Learning Data Group List")
	@GetMapping
	public List<TurDataGroup> turDataGroupList() throws JSONException {
		return this.turDataGroupRepository.findAll();
	}

	@ApiOperation(value = "Show a Machine Learning Data Group")
	@GetMapping("/{id}")
	public TurDataGroup turDataGroupGet(@PathVariable int id) throws JSONException {
		return this.turDataGroupRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning Data Group")
	@PutMapping("/{id}")
	public TurDataGroup turDataGroupUpdate(@PathVariable int id, @RequestBody TurDataGroup turDataGroup) throws Exception {
		TurDataGroup turDataGroupEdit = this.turDataGroupRepository.findById(id);
		turDataGroupEdit.setName(turDataGroup.getName());
		turDataGroupEdit.setDescription(turDataGroup.getDescription());
		this.turDataGroupRepository.save(turDataGroupEdit);
		return turDataGroupEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Data Group")
	@DeleteMapping("/{id}")
	public boolean turDataGroupDelete(@PathVariable int id) throws Exception {
		this.turDataGroupRepository.delete(id);
		return true;

	}

	@ApiOperation(value = "Create a Machine Learning Data Group")
	@PostMapping
	public TurDataGroup turDataGroupAdd(@RequestBody TurDataGroup turDataGroup) throws Exception {
		this.turDataGroupRepository.save(turDataGroup);
		return turDataGroup;

	}
}
