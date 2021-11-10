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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
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
import com.viglet.turing.converse.TurConverseSE;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseEvent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseParameter;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConversePrompt;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseEventRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseParameterRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePromptRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/converse/intent")
@Tag(name = "Converse Intent", description = "Converse Intent API")
public class TurConverseIntentAPI {

	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConverseAgentRepository turConverseAgentRepository;
	@Autowired
	private TurConverseContextRepository turConverseContextRepository;
	@Autowired
	private TurConverseEventRepository turConverseEventRepository;
	@Autowired
	private TurConverseParameterRepository turConverseParameterRepository;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	private TurConversePromptRepository turConversePromptRepository;
	@Autowired
	private TurConverseSE turConverseSE;

	@Operation(summary = "Converse Intent List")
	@GetMapping
	public List<TurConverseIntent> turConverseIntentList() throws JSONException {
		return this.turConverseIntentRepository.findAll();
	}

	@Operation(summary = "Show a Converse Intent")
	@GetMapping("/{id}")
	public TurConverseIntent turConverseIntentGet(@PathVariable String id) {
		return getIntent(id);
	}

	private TurConverseIntent getIntent(String id) {
		return this.turConverseIntentRepository.findById(id).map(turConverseIntent -> {
			turConverseIntent
					.setContextInputs(turConverseContextRepository.findByIntentInputs_Id(turConverseIntent.getId()));
			turConverseIntent
					.setContextOutputs(turConverseContextRepository.findByIntentOutputs_Id(turConverseIntent.getId()));
			turConverseIntent.setEvents(turConverseEventRepository.findByIntent(turConverseIntent));
			turConverseIntent.setParameters(turConverseParameterRepository.findByIntent(turConverseIntent));

			for (TurConverseParameter parameter : turConverseIntent.getParameters()) {
				parameter.setPrompts(turConversePromptRepository.findByParameter(parameter));
			}
			turConverseIntent.setPhrases(turConversePhraseRepository.findByIntent(turConverseIntent));
			turConverseIntent.setResponses(turConverseResponseRepository.findByIntent(turConverseIntent));
			return turConverseIntent;
		}).orElse(new TurConverseIntent());

	}

	@Operation(summary = "Update a Converse Intent")
	@PutMapping("/{id}")
	public TurConverseIntent turConverseIntentUpdate(@JsonProperty("agent") String agent, @PathVariable String id,
			@RequestBody TurConverseIntent turConverseIntent) throws Exception {
		return turConverseIntentRepository.findById(id).map(turConverseIntentEdit -> {
			turConverseIntentEdit.setActionName(turConverseIntent.getActionName());
			turConverseIntentEdit.setParameters(turConverseIntent.getParameters());
			turConverseIntentEdit.setContextInputs(turConverseIntent.getContextInputs());
			turConverseIntentEdit.setContextOutputs(turConverseIntent.getContextOutputs());
			turConverseIntentEdit.setEvents(turConverseIntent.getEvents());
			turConverseIntentEdit.setName(turConverseIntent.getName());
			turConverseIntentEdit.setParameters(turConverseIntent.getParameters());
			turConverseIntentEdit.setPhrases(turConverseIntent.getPhrases());
			turConverseIntentEdit.setResponses(turConverseIntent.getResponses());

			this.turConverseIntentRepository.save(turConverseIntentEdit);

			List<String> inputsIds = this.addInputContexts(id, turConverseIntent, turConverseIntentEdit);

			this.removeInputContexts(id, turConverseIntentEdit, inputsIds);

			List<String> outputsIds = addOutputContext(id, turConverseIntent, turConverseIntentEdit);

			this.removeOutputContexts(id, turConverseIntentEdit, outputsIds);

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

					Set<TurConversePrompt> prompts = parameter.getPrompts();
					for (TurConversePrompt prompt : prompts) {
						if (prompt != null) {
							prompt.setParameter(parameter);
							turConversePromptRepository.save(prompt);
						}
					}
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

			turConverseSE.index(turConverseIntentEdit);
			return this.getIntent(turConverseIntentEdit.getId());
		}).orElse(new TurConverseIntent());
	}

	private List<String> addOutputContext(String id, TurConverseIntent turConverseIntent,
			TurConverseIntent turConverseIntentEdit) {
		Set<TurConverseContext> outputs = turConverseIntent.getContextOutputs();
		List<String> outputsIds = new ArrayList<>();
		for (TurConverseContext output : outputs) {
			if (output != null) {
				if (output.getId() != null) {
					outputsIds.add(output.getId());
					Optional<TurConverseContext> contextOutput = turConverseContextRepository.findById(output.getId());
					if (contextOutput.isPresent()) {
						Set<TurConverseIntent> intents = contextOutput.get().getIntentOutputs();
						boolean found = false;
						for (TurConverseIntent intent : intents) {
							if (intent.getId().equals(id))
								found = true;
						}
						if (!found)
							contextOutput.get().getIntentOutputs().add(turConverseIntentEdit);

						turConverseContextRepository.save(contextOutput.get());
					}
				} else {

					Set<TurConverseContext> contextOutputs = turConverseContextRepository
							.findByAgentAndText(turConverseIntentEdit.getAgent(), output.getText());
					if (contextOutputs.isEmpty()) {
						TurConverseContext turConverseContext = new TurConverseContext();
						turConverseContext.setAgent(output.getAgent());
						Set<TurConverseIntent> intentOutputs = new HashSet<>();
						intentOutputs.add(turConverseIntentEdit);
						turConverseContext.setIntentOutputs(intentOutputs);
						turConverseContext.setText(output.getText());
						turConverseContextRepository.save(turConverseContext);
						outputsIds.add(turConverseContext.getId());
					} else {
						Iterator<TurConverseContext> iter = contextOutputs.iterator();
						TurConverseContext contextOutput = iter.next();
						contextOutput.getIntentOutputs().add(turConverseIntentEdit);
						turConverseContextRepository.save(contextOutput);
						outputsIds.add(contextOutput.getId());
					}

				}
			}
		}
		return outputsIds;
	}

	private List<String> addInputContexts(String id, TurConverseIntent turConverseIntent,
			TurConverseIntent turConverseIntentEdit) {
		Set<TurConverseContext> inputs = turConverseIntent.getContextInputs();
		List<String> inputsIds = new ArrayList<>();
		for (TurConverseContext input : inputs) {
			if (input != null) {
				if (input.getId() != null) {
					inputsIds.add(input.getId());
					Optional<TurConverseContext> contextInput = turConverseContextRepository.findById(input.getId());
					if (contextInput.isPresent()) {
						Set<TurConverseIntent> intents = contextInput.get().getIntentInputs();
						boolean found = false;
						for (TurConverseIntent intent : intents) {
							if (intent.getId().equals(id))
								found = true;
						}
						if (!found)
							contextInput.get().getIntentInputs().add(turConverseIntentEdit);

						turConverseContextRepository.save(contextInput.get());
					}
				} else {

					Set<TurConverseContext> contextInputs = turConverseContextRepository
							.findByAgentAndText(turConverseIntentEdit.getAgent(), input.getText());
					if (contextInputs.isEmpty()) {
						TurConverseContext turConverseContext = new TurConverseContext();
						turConverseContext.setAgent(input.getAgent());
						Set<TurConverseIntent> intentInputs = new HashSet<>();
						intentInputs.add(turConverseIntentEdit);
						turConverseContext.setIntentInputs(intentInputs);
						turConverseContext.setText(input.getText());
						turConverseContextRepository.save(turConverseContext);
						inputsIds.add(turConverseContext.getId());
					} else {
						Iterator<TurConverseContext> iter = contextInputs.iterator();
						TurConverseContext contextInput = iter.next();
						contextInput.getIntentInputs().add(turConverseIntentEdit);
						turConverseContextRepository.save(contextInput);
						inputsIds.add(contextInput.getId());
					}

				}
			}
		}
		return inputsIds;
	}

	private void removeInputContexts(String id, TurConverseIntent turConverseIntentEdit, List<String> inputsIds) {
		Set<TurConverseContext> contextInputs = turConverseContextRepository
				.findByIntentInputs_Id(turConverseIntentEdit.getId());
		for (TurConverseContext contextInput : contextInputs) {
			if (!inputsIds.contains(contextInput.getId())) {
				Set<TurConverseIntent> newIntentInput = new HashSet<>();
				for (TurConverseIntent intentInput : contextInput.getIntentInputs()) {
					if (!intentInput.getId().equals(id))
						newIntentInput.add(intentInput);
				}
				contextInput.setIntentInputs(newIntentInput);
				turConverseContextRepository.save(contextInput);
			}
		}
	}

	private void removeOutputContexts(String id, TurConverseIntent turConverseIntentEdit, List<String> outputsIds) {
		Set<TurConverseContext> contextOutputs = turConverseContextRepository
				.findByIntentOutputs_Id(turConverseIntentEdit.getId());
		for (TurConverseContext contextOutput : contextOutputs) {
			if (!outputsIds.contains(contextOutput.getId())) {
				Set<TurConverseIntent> newIntentOutput = new HashSet<>();
				for (TurConverseIntent intentOutput : contextOutput.getIntentOutputs()) {
					if (!intentOutput.getId().equals(id))
						newIntentOutput.add(intentOutput);
				}
				contextOutput.setIntentOutputs(newIntentOutput);
				turConverseContextRepository.save(contextOutput);
			}
		}
	}

	@Transactional
	@Operation(summary = "Delete a Converse Intent")
	@DeleteMapping("/{id}")
	public boolean turConverseIntentDelete(@PathVariable String id) {
		this.turConverseIntentRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Converse Intent")
	@PostMapping
	public TurConverseIntent turConverseIntentAdd(@JsonProperty("agent") String agent,
			@RequestBody TurConverseIntent turConverseIntent) {

		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(turConverseIntent.getAgent().getId())
				.orElse(null);
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

		Set<TurConverseParameter> parameters = turConverseIntent.getParameters();
		for (TurConverseParameter parameter : parameters) {
			if (parameter != null) {
				parameter.setIntent(turConverseIntent);
				turConverseParameterRepository.save(parameter);

				Set<TurConversePrompt> prompts = parameter.getPrompts();
				for (TurConversePrompt prompt : prompts) {
					if (prompt != null) {
						prompt.setParameter(parameter);
						turConversePromptRepository.save(prompt);
					}
				}
			}
		}
		turConverseSE.index(turConverseIntent);
		return turConverseIntent;

	}

	@Operation(summary = "Converse Intent Model")
	@GetMapping("/model")
	public TurConverseIntent turConverseIntentModel() {
		return new TurConverseIntent();
	}

}
