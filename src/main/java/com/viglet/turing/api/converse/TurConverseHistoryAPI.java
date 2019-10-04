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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChatResponse;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatRepository;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatResponseRepository;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/history")
@Api(tags = "Converse History", description = "Converse History API")
public class TurConverseHistoryAPI {
	@Autowired
	private TurConverseChatRepository turConverseChatRepository;
	@Autowired
	private TurConverseChatResponseRepository turConverseChatResponseRepository;
	
	
	@ApiOperation(value = "Converse History List")
	@GetMapping
	public List<TurConverseChat> turConverseHistoryList(){
		return this.turConverseChatRepository.findAll();
	}
	
	@ApiOperation(value = "Show a Converse Conversation")
	@GetMapping("/{id}")
	public TurConverseChat turConverseHistoryGet(@PathVariable String id) {
		TurConverseChat turConverseChat = turConverseChatRepository.findById(id).get();
		List<TurConverseChatResponse> responses = turConverseChatResponseRepository.findByChat(turConverseChat);
		turConverseChat.setResponses(responses);
		return turConverseChat;
	}
	
	@Transactional
	@ApiOperation(value = "Delete a Converse Conversation")
	@DeleteMapping("/{id}")
	public boolean turConverseHistoryDelete(@PathVariable String id) {
		this.turConverseChatRepository.delete(id);
		return true;
	}
}
