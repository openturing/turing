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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChatResponse;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatRepository;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatResponseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/converse/history")
@Tag(name ="Converse History", description = "Converse History API")
public class TurConverseHistoryAPI {
	private final TurConverseChatRepository turConverseChatRepository;
	private final TurConverseChatResponseRepository turConverseChatResponseRepository;
	@Inject
	public TurConverseHistoryAPI(TurConverseChatRepository turConverseChatRepository,
								 TurConverseChatResponseRepository turConverseChatResponseRepository) {
		this.turConverseChatRepository = turConverseChatRepository;
		this.turConverseChatResponseRepository = turConverseChatResponseRepository;
	}

	@Operation(summary = "Converse Training List")
	@GetMapping
	public List<TurConverseChat> turConverseHistoryList() {
		return this.turConverseChatRepository.findAll();
	}

	@Operation(summary = "Show a Converse Training")
	@GetMapping("/{id}")
	public TurConverseChat turConverseTrainingGet(@PathVariable String id) {
		return turConverseChatRepository.findById(id).map(turConverseChat -> {
			List<TurConverseChatResponse> responses = turConverseChatResponseRepository.findByChat(turConverseChat);
			turConverseChat.setResponses(responses);
			return turConverseChat;
		}).orElse(new TurConverseChat());

	}

}
