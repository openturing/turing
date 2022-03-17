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

import com.viglet.turing.bean.ml.sentence.TurMLSentenceBean;
import com.viglet.turing.persistence.bean.storage.TurDataGroupSentenceBean;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/sentence")
@Tag(name = "Machine Learning Sentence by Group", description = "Machine Learning Sentence by Group API")
public class TurMLDataGroupSentenceAPI {

	@Autowired
	private TurDataGroupRepository turDataGroupRepository;
	@Autowired
	private TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	private TurMLCategoryRepository turMLCategoryRepository;

	@Operation(summary = "Machine Learning Data Group Sentence List")
	@GetMapping
	public List<TurDataGroupSentence> turDataGroupSentenceList(@PathVariable int dataGroupId) {
		return turDataGroupRepository.findById(dataGroupId).map(this.turDataGroupSentenceRepository::findByTurDataGroup)
				.orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Machine Learning Data Group Sentence")
	@GetMapping("/{id}")
	public TurDataGroupSentence turDataGroupSentenceGet(@PathVariable int dataGroupId, @PathVariable int id) {
		return this.turDataGroupSentenceRepository.findById(id);
	}

	@Operation(summary = "Update a Machine Learning Data Group Sentence")
	@PutMapping("/{id}")
	public TurDataGroupSentence turDataGroupSentenceUpdate(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurDataGroupSentenceBean turDataGroupSentenceBean) {
		TurDataGroupSentence turDataGroupSentenceEdit = this.turDataGroupSentenceRepository.findById(id);
		turDataGroupSentenceEdit.setSentence(turDataGroupSentenceBean.getSentence());
		turDataGroupSentenceEdit.setTurMLCategory(
				turMLCategoryRepository.findById(turDataGroupSentenceBean.getTurMLCategory()).orElse(null));
		this.turDataGroupSentenceRepository.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Group Sentence")
	@DeleteMapping("/{id}")
	public boolean turDataGroupSentenceDelete(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupSentenceRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Group Sentence")
	@PostMapping
	public TurDataGroupSentence turDataGroupSentenceAdd(@PathVariable int dataGroupId,
			@RequestBody TurMLSentenceBean turMLSentenceBean) {
		TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
		return this.turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			turDataGroupSentence.setSentence(turMLSentenceBean.getSentence());
			turDataGroupSentence.setTurDataGroup(turDataGroup);

			this.turMLCategoryRepository.findById(turMLSentenceBean.getTurMLCategoryId())
					.ifPresent(turDataGroupSentence::setTurMLCategory);

			this.turDataGroupSentenceRepository.save(turDataGroupSentence);
			return turDataGroupSentence;
		}).orElse(turDataGroupSentence);

	}
}
