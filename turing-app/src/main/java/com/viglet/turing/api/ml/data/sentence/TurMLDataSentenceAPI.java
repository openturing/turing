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

package com.viglet.turing.api.ml.data.sentence;

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

import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/data/sentence")
@Tag(name = "Machine Learning Sentence", description = "Machine Learning Sentence API")
public class TurMLDataSentenceAPI {

	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;

	@Operation(summary = "Machine Learning Data Sentence List")
	@GetMapping
	public List<TurDataGroupSentence> turDataSentenceList() {
		return this.turDataGroupSentenceRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Data Sentence")
	@GetMapping("/{id}")
	public TurDataGroupSentence turDataSentenceGet(@PathVariable int id) {
		return turDataGroupSentenceRepository.findById(id);
	}

	@Operation(summary = "Update a Machine Learning Data Sentence")
	@PutMapping("/{id}")
	public TurDataGroupSentence turDataSentenceUpdate(@PathVariable int id,
			@RequestBody TurDataGroupSentence turDataGroupSentence) {

		TurDataGroupSentence turDataGroupSentenceEdit = turDataGroupSentenceRepository.findById(id);
		turDataGroupSentenceEdit.setSentence(turDataGroupSentence.getSentence());
		turDataGroupSentenceEdit.setTurData(turDataGroupSentence.getTurData());
		turDataGroupSentenceEdit.setTurMLCategory(turDataGroupSentence.getTurMLCategory());
		this.turDataGroupSentenceRepository.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Sentence")
	@DeleteMapping("/{id}")
	public boolean turDataSentenceDelete(@PathVariable int id) throws Exception {
		this.turDataGroupSentenceRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Sentence")
	@PostMapping
	public TurDataGroupSentence turDataSentenceAdd(@RequestBody TurDataGroupSentence turDataSentence) throws Exception {
		this.turDataGroupSentenceRepository.save(turDataSentence);
		return turDataSentence;

	}
}
