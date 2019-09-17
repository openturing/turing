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

package com.viglet.turing.api.converse;

import java.util.Arrays;
import java.util.HashSet;
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

import com.viglet.turing.persistence.model.converse.TurConverseIntent;
import com.viglet.turing.persistence.repository.converse.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.TurConverseResponseRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/intent")
@Api(tags = "Converse Intent", description = "Converse Intent API")
public class TurConverseIntentAPI {

	@Autowired
	TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	TurConverseContextRepository turConverseContextRepository;
	@Autowired
	TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	TurConverseResponseRepository turConverseResponseRepository;

	@ApiOperation(value = "Converse Intent List")
	@GetMapping
	public List<TurConverseIntent> turConverseIntentList() throws JSONException {
		return this.turConverseIntentRepository.findAll();
	}

	@ApiOperation(value = "Show a Converse Intent")
	@GetMapping("/{id}")
	public TurConverseIntent turConverseIntentGet(@PathVariable String id) {
		TurConverseIntent turConverseIntent = this.turConverseIntentRepository.findById(id).get();
		turConverseIntent.setContextInputs(
				turConverseContextRepository.findByIntentInputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setContextOutputs(
				turConverseContextRepository.findByIntentOutputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setPhrases(turConversePhraseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setResponses(turConverseResponseRepository.findByIntent(turConverseIntent));
		return turConverseIntent;
	}

	@ApiOperation(value = "Update a Converse Intent")
	@PutMapping("/{id}")
	public TurConverseIntent turConverseIntentUpdate(@PathVariable String id,
			@RequestBody TurConverseIntent turConverseIntent) throws Exception {
		TurConverseIntent turConverseIntentEdit = this.turConverseIntentRepository.findById(id).get();

		this.turConverseIntentRepository.save(turConverseIntentEdit);
		return turConverseIntentEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Converse Intent")
	@DeleteMapping("/{id}")
	public boolean turConverseIntentDelete(@PathVariable String id) {
		this.turConverseIntentRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Converse Intent")
	@PostMapping
	public TurConverseIntent turConverseIntentAdd(@RequestBody TurConverseIntent turConverseIntent) throws Exception {
		this.turConverseIntentRepository.save(turConverseIntent);
		return turConverseIntent;

	}
}
