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
import java.util.Set;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.viglet.turing.converse.TurConverseIndex;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseEvent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseParameter;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseEventRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseParameterRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/intent")
@Api(tags = "Converse Intent", description = "Converse Intent API")
public class TurConverseIntentAPI {

	@Autowired
	TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	TurConverseAgentRepository turConverseAgentRepository;
	@Autowired
	TurConverseContextRepository turConverseContextRepository;
	@Autowired
	TurConverseEventRepository turConverseEventRepository;
	@Autowired
	TurConverseParameterRepository turConverseParameterRepository;
	@Autowired
	TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	TurConverseIndex turConverseIndex;
	
	@ApiOperation(value = "Converse Intent List")
	@GetMapping
	public List<TurConverseIntent> turConverseIntentList() throws JSONException {
		return this.turConverseIntentRepository.findAll();
	}

	@ApiOperation(value = "Show a Converse Intent")
	@GetMapping("/{id}")
	public TurConverseIntent turConverseIntentGet(@PathVariable String id) {
		TurConverseIntent turConverseIntent = getIntent(id);
		return turConverseIntent;
	}

	private TurConverseIntent getIntent(String id) {
		TurConverseIntent turConverseIntent = this.turConverseIntentRepository.findById(id).get();
		//turConverseIntent.setAgent(turConverseAgentRepository.find);
		turConverseIntent.setContextInputs(
				turConverseContextRepository.findByIntentInputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setContextOutputs(
				turConverseContextRepository.findByIntentOutputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setEvents(turConverseEventRepository.findByIntent(turConverseIntent));
		turConverseIntent.setParameters(turConverseParameterRepository.findByIntent(turConverseIntent));
		turConverseIntent.setPhrases(turConversePhraseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setResponses(turConverseResponseRepository.findByIntent(turConverseIntent));
		return turConverseIntent;
	}

	@ApiOperation(value = "Update a Converse Intent")
	@PutMapping("/{id}")
	public TurConverseIntent turConverseIntentUpdate(@JsonProperty("agent") String agent, @PathVariable String id,
			@RequestBody TurConverseIntent turConverseIntent) throws Exception {
		TurConverseIntent turConverseIntentEdit = this.turConverseIntentRepository.findById(id).get();

		turConverseIntentEdit.setParameters(turConverseIntent.getParameters());
		turConverseIntentEdit.setContextInputs(turConverseIntent.getContextInputs());
		turConverseIntentEdit.setContextOutputs(turConverseIntent.getContextOutputs());
		turConverseIntentEdit.setEvents(turConverseIntent.getEvents());
		turConverseIntentEdit.setName(turConverseIntent.getName());
		turConverseIntentEdit.setParameters(turConverseIntent.getParameters());
		turConverseIntentEdit.setPhrases(turConverseIntent.getPhrases());
		turConverseIntentEdit.setResponses(turConverseIntent.getResponses());

		this.turConverseIntentRepository.save(turConverseIntentEdit);

		Set<TurConverseContext> inputs = turConverseIntent.getContextInputs();
		for (TurConverseContext input : inputs) {
			if (input != null) {
				Set<TurConverseIntent> intents = new HashSet<>();
				intents.add(turConverseIntentEdit);
				input.setIntentInputs(intents);
				turConverseContextRepository.save(input);
			}
		}

		Set<TurConverseContext> outputs = turConverseIntent.getContextOutputs();
		for (TurConverseContext output : outputs) {
			if (output != null) {
				Set<TurConverseIntent> intents = new HashSet<>();
				intents.add(turConverseIntentEdit);
				output.setIntentOutputs(intents);
				turConverseContextRepository.save(output);
			}
		}

		Set<TurConverseEvent> events = turConverseIntent.getEvents();
		for (TurConverseEvent event : events) {
			if (event != null) {
				event.setIntent(turConverseIntentEdit);
				turConverseEventRepository.save(event);
			}
		}

		Set<TurConverseParameter> parameters = turConverseIntent.getParameters();
		for (TurConverseParameter parameter : parameters) {
			if (parameter != null) {
				parameter.setIntent(turConverseIntentEdit);
				turConverseParameterRepository.save(parameter);
			}
		}
		
		Set<TurConversePhrase> phrases = turConverseIntent.getPhrases();
		for (TurConversePhrase phrase : phrases) {
			if (phrase != null) {
				phrase.setIntent(turConverseIntentEdit);
				turConversePhraseRepository.save(phrase);
			}
		}

		Set<TurConverseResponse> responses = turConverseIntent.getResponses();
		for (TurConverseResponse response : responses) {
			if (response != null) {
				response.setIntent(turConverseIntentEdit);
				turConverseResponseRepository.save(response);			
			}
		}

		turConverseIndex.index(turConverseIntentEdit);
		return this.getIntent(turConverseIntentEdit.getId());
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
	public TurConverseIntent turConverseIntentAdd(@JsonProperty("agent") String agent, @RequestBody TurConverseIntent turConverseIntent) throws Exception {
		
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(turConverseIntent.getAgent().getId()).orElse(null);
		turConverseIntent.setAgent(turConverseAgent);
		this.turConverseIntentRepository.save(turConverseIntent);

		Set<TurConverseContext> inputs = turConverseIntent.getContextInputs();
		for (TurConverseContext input : inputs) {
			Set<TurConverseIntent> intents = new HashSet<>();
			intents.add(turConverseIntent);
			input.setIntentInputs(intents);
			turConverseContextRepository.save(input);
		}

		Set<TurConverseContext> outputs = turConverseIntent.getContextOutputs();
		for (TurConverseContext output : outputs) {
			Set<TurConverseIntent> intents = new HashSet<>();
			intents.add(turConverseIntent);
			output.setIntentOutputs(intents);
			turConverseContextRepository.save(output);
		}

		Set<TurConverseEvent> events = turConverseIntent.getEvents();
		for (TurConverseEvent event : events) {
			event.setIntent(turConverseIntent);
			turConverseEventRepository.save(event);
		}

		Set<TurConversePhrase> phrases = turConverseIntent.getPhrases();
		for (TurConversePhrase phrase : phrases) {
			phrase.setIntent(turConverseIntent);
			turConversePhraseRepository.save(phrase);
		}

		Set<TurConverseResponse> responses = turConverseIntent.getResponses();
		for (TurConverseResponse response : responses) {
			response.setIntent(turConverseIntent);
			turConverseResponseRepository.save(response);
		}
		
		turConverseIndex.index(turConverseIntent);
		return turConverseIntent;

	}

	@ApiOperation(value = "Converse Intent Model")
	@GetMapping("/model")
	public TurConverseIntent turConverseIntentModel() {
		TurConverseIntent turConverseIntent = new TurConverseIntent();
		return turConverseIntent;
	}

}
