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

package com.viglet.turing.persistence.repository.converse.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChatResponse;

public interface TurConverseChatResponseRepository extends JpaRepository<TurConverseChatResponse, String> {

	List<TurConverseChatResponse> findAll();
	
	Optional<TurConverseChatResponse> findById(String id);

	List<TurConverseChatResponse> findByChat(TurConverseChat turConverseChat);
	
	List<TurConverseChatResponse> findByChatOrderByDate(TurConverseChat turConverseChat);
	
	List<TurConverseChatResponse> findByChatAndIsUser(TurConverseChat turConverseChat, boolean isUser);
	
	
	List<TurConverseChatResponse> findByChatAndIsUserAndParameterNameOrderByDateDesc(TurConverseChat turConverseChat, boolean isUser, String parameterName);
	
	
	int countByChatAndIsUser(TurConverseChat turConverseChat, boolean isUser);
	
	int countByChatAndIsUserAndIntentIdIsNull(TurConverseChat turConverseChat, boolean isUser);
	
	@SuppressWarnings("unchecked")
	TurConverseChatResponse save(TurConverseChatResponse turConverseChat);

	@Modifying
	@Query("delete from  TurConverseChatResponse ccr where ccr.id = ?1")
	void delete(String id);

	
}
