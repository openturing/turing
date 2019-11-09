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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.apache.commons.compress.archivers.ArchiveException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.bean.converse.TurConverseAgentResponse;
import com.viglet.turing.converse.TurConverse;
import com.viglet.turing.converse.TurConverseSE;
import com.viglet.turing.converse.exchange.TurConverseImportExchange;
import com.viglet.turing.converse.exchange.agent.TurConverseAgentExchange;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.entity.TurConverseEntity;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatRepository;
import com.viglet.turing.persistence.repository.converse.entity.TurConverseEntityRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/agent")
@Api(tags = "Converse Agent", description = "Converse Agent API")
public class TurConverseAgentAPI {

	@Autowired
	private TurConverseAgentRepository turConverseAgentRepository;
	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConverseEntityRepository turConverseEntityRepository;
	@Autowired
	private TurConverseContextRepository turConverseContextRepository;
	@Autowired
	private TurConverseChatRepository turConverseChatRepository;
	@Autowired
	private TurConverseImportExchange turConverseImportExchange;
	@Autowired
	private TurConverse turConverse;
	@Autowired
	private TurConverseSE turConverseSE;

	@ApiOperation(value = "Converse Agent List")
	@GetMapping
	public List<TurConverseAgent> turConverseAgentList() {
		return this.turConverseAgentRepository.findAll();
	}

	@ApiOperation(value = "Show a Converse Agent")
	@GetMapping("/{id}")
	public TurConverseAgent turConverseAgentGet(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		return turConverseAgent;
	}

	@ApiOperation(value = "Show a Converse Intent List")
	@GetMapping("/{id}/intents")
	public Set<TurConverseIntent> turConverseAgentIntentsGet(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		return turConverseIntentRepository.findByAgent(turConverseAgent);
	}

	@ApiOperation(value = "Show a Converse Entity List")
	@GetMapping("/{id}/entities")
	public Set<TurConverseEntity> turConverseAgentEntitiesGet(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		return turConverseEntityRepository.findByAgent(turConverseAgent);
	}
	
	@ApiOperation(value = "Show a Converse Context List")
	@GetMapping("/{id}/contexts")
	public Set<TurConverseContext> turConverseAgentContextsGet(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		return turConverseContextRepository.findByAgent(turConverseAgent);
	}

	@ApiOperation(value = "Create a Converse Agent")
	@PostMapping
	public TurConverseAgent turConverseAgentAdd(@RequestBody TurConverseAgent turConverseAgent) {
		return turConverseAgentRepository.save(turConverseAgent);
	}

	@ApiOperation(value = "Update a Converse Agent")
	@PutMapping("/{id}")
	public TurConverseAgent turConverseAgentUpdate(@PathVariable String id,
			@RequestBody TurConverseAgent turConverseAgent) {
		TurConverseAgent turConverseAgentEdit = turConverseAgentRepository.findById(turConverseAgent.getId()).get();
		
		turConverseAgentEdit.setCore(turConverseAgent.getCore());
		turConverseAgentEdit.setDescription(turConverseAgent.getDescription());
		turConverseAgentEdit.setLanguage(turConverseAgent.getLanguage());
		turConverseAgentEdit.setName(turConverseAgent.getName());
		turConverseAgentEdit.setTurSEInstance(turConverseAgent.getTurSEInstance());
		
		
		return turConverseAgentRepository.save(turConverseAgentEdit);

	}

	@Transactional
	@ApiOperation(value = "Delete a Converse Agent")
	@DeleteMapping("/{id}")
	public boolean turConverseAgentDelete(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		Set<TurConverseContext> turConverseContexts = turConverseContextRepository.findByAgent(turConverseAgent);
		for (TurConverseContext context :turConverseContexts) {
			context.setIntentInputs(null);
			context.setIntentOutputs(null);
			turConverseContextRepository.saveAndFlush(context);
		}
		this.turConverseAgentRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Converse Agent Model")
	@GetMapping("/model")
	public TurConverseAgent turConverseAgentModel() {
		TurConverseAgent turConverseAgent = new TurConverseAgent();
		return turConverseAgent;
	}

	@ApiOperation(value = "Rebuild Chat")
	@GetMapping("/{id}/rebuild")
	public boolean turConverseAgentRebuild(@PathVariable String id) {

		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		Set<TurConverseIntent> turConverseIntents = turConverseIntentRepository.findByAgent(turConverseAgent);
		turConverseSE.desindexAll(turConverseAgent);

		for (TurConverseIntent turConverseIntent : turConverseIntents) {
			turConverseSE.index(turConverseIntent);
		}
		return true;
	}

	@ApiOperation(value = "Converse Chat")
	@GetMapping("/{id}/chat")
	public TurConverseAgentResponse turConverseAgentChat(@PathVariable String id,
			@RequestParam(required = false, name = "q") String q,
			@RequestParam(required = false, name = "start") boolean start, HttpSession session) {

		if (start)
			turConverse.cleanSession(session);

		turConverse.showSession(session);

		boolean hasParameter = session.getAttribute("hasParameter") != null
				? (boolean) session.getAttribute("hasParameter")
				: false;

		TurConverseChat chat = null;
		if (session != null && session.getId() != null) {
			chat = turConverseChatRepository.findBySession(session.getId());
			if (chat == null) {
				
				chat = new TurConverseChat();
				chat.setSession(session.getId());
				chat.setAgent(turConverseAgentRepository.findById(id).orElse(null));
				chat.setDate(new Date());
				turConverseChatRepository.save(chat);
			}
		}

		turConverse.saveChatResponseUser(q, chat, session);

		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();

		if (hasParameter) {
			turConverse.getChatParameter(chat, session, turConverseAgentResponse);
		} else {
			String nextContext = (String) session.getAttribute("nextContext");
			turConverseAgentResponse = turConverse.interactionNested(chat, q, nextContext, session);
		}
		turConverse.saveChatResponseBot(chat, turConverseAgentResponse, session);

		return turConverseAgentResponse;
	}


	@PostMapping("/import")
	@Transactional
	public TurConverseAgentExchange shImport(@RequestParam("file") MultipartFile multipartFile)
			throws IllegalStateException, IOException, ArchiveException {
		return turConverseImportExchange.importFromMultipartFile(multipartFile);
	}
}
