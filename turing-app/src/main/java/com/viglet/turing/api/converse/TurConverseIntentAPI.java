/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.converse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Inject;
import com.viglet.turing.converse.TurConverseSE;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.*;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.json.JSONException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/converse/intent")
@Tag(name = "Converse Intent", description = "Converse Intent API")
public class TurConverseIntentAPI {

	private final TurConverseIntentRepository turConverseIntentRepository;
	private final TurConverseAgentRepository turConverseAgentRepository;
	private final TurConverseContextRepository turConverseContextRepository;
	private final TurConverseEventRepository turConverseEventRepository;
	private final TurConverseParameterRepository turConverseParameterRepository;
	private final TurConversePhraseRepository turConversePhraseRepository;
	private final TurConverseResponseRepository turConverseResponseRepository;
	private final TurConversePromptRepository turConversePromptRepository;
	private final TurConverseSE turConverseSE;

	@Inject
	public TurConverseIntentAPI(TurConverseIntentRepository turConverseIntentRepository,
								TurConverseAgentRepository turConverseAgentRepository,
								TurConverseContextRepository turConverseContextRepository,
								TurConverseEventRepository turConverseEventRepository,
								TurConverseParameterRepository turConverseParameterRepository,
								TurConversePhraseRepository turConversePhraseRepository,
								TurConverseResponseRepository turConverseResponseRepository,
								TurConversePromptRepository turConversePromptRepository,
								TurConverseSE turConverseSE) {
		this.turConverseIntentRepository = turConverseIntentRepository;
		this.turConverseAgentRepository = turConverseAgentRepository;
		this.turConverseContextRepository = turConverseContextRepository;
		this.turConverseEventRepository = turConverseEventRepository;
		this.turConverseParameterRepository = turConverseParameterRepository;
		this.turConversePhraseRepository = turConversePhraseRepository;
		this.turConverseResponseRepository = turConverseResponseRepository;
		this.turConversePromptRepository = turConversePromptRepository;
		this.turConverseSE = turConverseSE;
	}

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
			@RequestBody TurConverseIntent turConverseIntent) {
		return turConverseIntentRepository.findById(id).map(turConverseIntentEdit -> {
			saveIntent(turConverseIntent, turConverseIntentEdit);

			List<String> inputsIds = this.addInputContexts(id, turConverseIntent, turConverseIntentEdit);

			removeInputContexts(id, turConverseIntentEdit, inputsIds);

			List<String> outputsIds = addOutputContext(id, turConverseIntent, turConverseIntentEdit);

			removeOutputContexts(id, turConverseIntentEdit, outputsIds);

			saveEvents(turConverseIntent, turConverseIntentEdit);

			savePrompts(turConverseIntent, turConverseIntentEdit);

			savePhrases(turConverseIntent, turConverseIntentEdit);

			saveResponses(turConverseIntent, turConverseIntentEdit);

			turConverseSE.index(turConverseIntentEdit);
			return this.getIntent(turConverseIntentEdit.getId());
		}).orElse(new TurConverseIntent());
	}

	private void saveIntent(TurConverseIntent turConverseIntent, TurConverseIntent turConverseIntentEdit) {
		turConverseIntentEdit.setActionName(turConverseIntent.getActionName());
		turConverseIntentEdit.setParameters(turConverseIntent.getParameters());
		turConverseIntentEdit.setContextInputs(turConverseIntent.getContextInputs());
		turConverseIntentEdit.setContextOutputs(turConverseIntent.getContextOutputs());
		turConverseIntentEdit.setEvents(turConverseIntent.getEvents());
		turConverseIntentEdit.setName(turConverseIntent.getName());
		turConverseIntentEdit.setParameters(turConverseIntent.getParameters());
		turConverseIntentEdit.setPhrases(turConverseIntent.getPhrases());
		turConverseIntentEdit.setResponses(turConverseIntent.getResponses());

		turConverseIntentRepository.save(turConverseIntentEdit);
	}

	private void saveEvents(TurConverseIntent turConverseIntent, TurConverseIntent turConverseIntentEdit) {
		Set<TurConverseEvent> events = turConverseIntent.getEvents();
		for (TurConverseEvent event : events) {
			if (event != null) {
				event.setIntent(turConverseIntentEdit);
				turConverseEventRepository.save(event);
			}
		}
	}

	private void savePrompts(TurConverseIntent turConverseIntent, TurConverseIntent turConverseIntentEdit) {
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
	}

	private void saveResponses(TurConverseIntent turConverseIntent, TurConverseIntent turConverseIntentEdit) {
		Set<TurConverseResponse> responses = turConverseIntent.getResponses();
		for (TurConverseResponse response : responses) {
			if (response != null) {
				response.setIntent(turConverseIntentEdit);
				turConverseResponseRepository.save(response);
			}
		}
	}

	private void savePhrases(TurConverseIntent turConverseIntent, TurConverseIntent turConverseIntentEdit) {
		Set<TurConversePhrase> phrases = turConverseIntent.getPhrases();
		for (TurConversePhrase phrase : phrases) {
			if (phrase != null) {
				phrase.setIntent(turConverseIntentEdit);
				turConversePhraseRepository.save(phrase);
			}
		}
	}

	private List<String> addOutputContext(String id, TurConverseIntent turConverseIntent,
			TurConverseIntent turConverseIntentEdit) {
		Set<TurConverseContext> outputs = turConverseIntent.getContextOutputs();
		List<String> outputsIds = new ArrayList<>();
		for (TurConverseContext output : outputs) {
			analyzeConverseContext(id, turConverseIntentEdit, outputsIds, output);
		}
		return outputsIds;
	}

	private void analyzeConverseContext(String id, TurConverseIntent turConverseIntentEdit, List<String> outputsIds,
			TurConverseContext output) {
		if (output != null) {
			if (output.getId() != null) {
				addIntentOutputToConverseContext(id, turConverseIntentEdit, outputsIds, output);
			} else {
				Set<TurConverseContext> contextOutputs = turConverseContextRepository
						.findByAgentAndText(turConverseIntentEdit.getAgent(), output.getText());
				if (contextOutputs.isEmpty()) {
					TurConverseContext turConverseContext = saveConverseContext(turConverseIntentEdit, output);
					outputsIds.add(turConverseContext.getId());
				} else {
					TurConverseContext contextOutput = saveContextOutput(turConverseIntentEdit, contextOutputs);
					outputsIds.add(contextOutput.getId());
				}

			}
		}
	}

	private void addIntentOutputToConverseContext(String id, TurConverseIntent turConverseIntentEdit,
			List<String> outputsIds, TurConverseContext output) {
		outputsIds.add(output.getId());
		Optional<TurConverseContext> contextOutput = turConverseContextRepository.findById(output.getId());
		if (contextOutput.isPresent()) {
			Set<TurConverseIntent> intents = contextOutput.get().getIntentOutputs();
			boolean found = false;
			for (TurConverseIntent intent : intents) {
                if (intent.getId().equals(id)) {
                    found = true;
                    break;
                }
			}
			if (!found)
				contextOutput.get().getIntentOutputs().add(turConverseIntentEdit);

			turConverseContextRepository.save(contextOutput.get());
		}
	}

	private TurConverseContext saveContextOutput(TurConverseIntent turConverseIntentEdit,
			Set<TurConverseContext> contextOutputs) {
		Iterator<TurConverseContext> iter = contextOutputs.iterator();
		TurConverseContext contextOutput = iter.next();
		contextOutput.getIntentOutputs().add(turConverseIntentEdit);
		turConverseContextRepository.save(contextOutput);
		return contextOutput;
	}

	private TurConverseContext saveConverseContext(TurConverseIntent turConverseIntentEdit, TurConverseContext output) {
		TurConverseContext turConverseContext = new TurConverseContext();
		turConverseContext.setAgent(output.getAgent());
		Set<TurConverseIntent> intentOutputs = new HashSet<>();
		intentOutputs.add(turConverseIntentEdit);
		turConverseContext.setIntentOutputs(intentOutputs);
		turConverseContext.setText(output.getText());
		turConverseContextRepository.save(turConverseContext);
		return turConverseContext;
	}

	private List<String> addInputContexts(String id, TurConverseIntent turConverseIntent,
			TurConverseIntent turConverseIntentEdit) {
		Set<TurConverseContext> inputs = turConverseIntent.getContextInputs();
		List<String> inputsIds = new ArrayList<>();
		for (TurConverseContext input : inputs) {
			processInput(id, turConverseIntentEdit, inputsIds, input);
		}
		return inputsIds;
	}

	private void processInput(String id, TurConverseIntent turConverseIntentEdit, List<String> inputsIds,
			TurConverseContext input) {
		if (input != null) {
			if (input.getId() != null) {
				addConverseContextFromId(id, turConverseIntentEdit, inputsIds, input);
			} else {
				Set<TurConverseContext> contextInputs = turConverseContextRepository
						.findByAgentAndText(turConverseIntentEdit.getAgent(), input.getText());
				if (contextInputs.isEmpty()) {
					addConverseContext(turConverseIntentEdit, inputsIds, input);
				} else {
					addConverseContextFromAgentAndText(turConverseIntentEdit, inputsIds, contextInputs);
				}

			}
		}
	}

	private void addConverseContext(TurConverseIntent turConverseIntentEdit, List<String> inputsIds,
			TurConverseContext input) {
		TurConverseContext turConverseContext = new TurConverseContext();
		turConverseContext.setAgent(input.getAgent());
		Set<TurConverseIntent> intentInputs = new HashSet<>();
		intentInputs.add(turConverseIntentEdit);
		turConverseContext.setIntentInputs(intentInputs);
		turConverseContext.setText(input.getText());
		turConverseContextRepository.save(turConverseContext);
		inputsIds.add(turConverseContext.getId());
	}

	private void addConverseContextFromAgentAndText(TurConverseIntent turConverseIntentEdit, List<String> inputsIds,
			Set<TurConverseContext> contextInputs) {
		Iterator<TurConverseContext> iter = contextInputs.iterator();
		TurConverseContext contextInput = iter.next();
		contextInput.getIntentInputs().add(turConverseIntentEdit);
		turConverseContextRepository.save(contextInput);
		inputsIds.add(contextInput.getId());
	}

	private void addConverseContextFromId(String id, TurConverseIntent turConverseIntentEdit, List<String> inputsIds,
			TurConverseContext input) {
		inputsIds.add(input.getId());
		Optional<TurConverseContext> contextInput = turConverseContextRepository.findById(input.getId());
		if (contextInput.isPresent()) {
			Set<TurConverseIntent> intents = contextInput.get().getIntentInputs();
			boolean found = false;
			for (TurConverseIntent intent : intents) {
				if (intent.getId().equals(id)) {
					found = true;
					break;
				}
			}
			if (!found)
				contextInput.get().getIntentInputs().add(turConverseIntentEdit);

			turConverseContextRepository.save(contextInput.get());
		}
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

		savePrompts(turConverseIntent, turConverseIntent);
		turConverseSE.index(turConverseIntent);
		return turConverseIntent;

	}

	@Operation(summary = "Converse Intent Model")
	@GetMapping("/model")
	public TurConverseIntent turConverseIntentModel() {
		return new TurConverseIntent();
	}

}
