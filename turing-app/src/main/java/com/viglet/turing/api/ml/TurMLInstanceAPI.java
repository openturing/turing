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

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.repository.ml.TurMLInstanceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml")
@Tag(name ="Machine Learning", description = "Machine Learning API")
public class TurMLInstanceAPI {

	@Autowired
	private TurMLInstanceRepository turMLInstanceRepository;

	@Operation(summary = "Machine Learning List")
	@GetMapping
	public List<TurMLInstance> turMLInstanceList() {
		return this.turMLInstanceRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning")
	@GetMapping("/{id}")
	public TurMLInstance turMLInstanceGEt(@PathVariable int id) {
		return this.turMLInstanceRepository.findById(id).orElse(new TurMLInstance());
	}

	@Operation(summary = "Update a Machine Learning")
	@PutMapping("/{id}")
	public TurMLInstance turMLInstanceUpdate(@PathVariable int id, @RequestBody TurMLInstance turMLInstance) {
		return this.turMLInstanceRepository.findById(id).map(turMLInstanceEdit -> {
			turMLInstanceEdit.setTitle(turMLInstance.getTitle());
			turMLInstanceEdit.setDescription(turMLInstance.getDescription());
			turMLInstanceEdit.setTurMLVendor(turMLInstance.getTurMLVendor());
			turMLInstanceEdit.setHost(turMLInstance.getHost());
			turMLInstanceEdit.setPort(turMLInstance.getPort());
			turMLInstanceEdit.setEnabled(turMLInstance.getEnabled());
			this.turMLInstanceRepository.save(turMLInstanceEdit);
			return turMLInstanceEdit;
		}).orElse(new TurMLInstance());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning")
	@DeleteMapping("/{id}")
	public boolean turMLInstanceDelete(@PathVariable int id) {
		this.turMLInstanceRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning")
	@PostMapping
	public TurMLInstance turMLInstanceAdd(@RequestBody TurMLInstance turMLInstance) {
		this.turMLInstanceRepository.save(turMLInstance);
		return turMLInstance;

	}
}