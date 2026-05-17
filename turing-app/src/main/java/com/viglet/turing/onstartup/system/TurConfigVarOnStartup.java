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
package com.viglet.turing.onstartup.system;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Component
@Transactional
public class TurConfigVarOnStartup {
	public static final String FIRST_TIME = "FIRST_TIME";
	public static final String DECIMAL_SEPARATOR = "GLOBAL_DECIMAL_SEPARATOR";
	public static final String PYTHON_EXECUTABLE = "GLOBAL_PYTHON_EXECUTABLE";
	public static final String PYTHON_REQUIREMENTS = "GLOBAL_PYTHON_REQUIREMENTS";
	public static final String DEFAULT_LLM = "GLOBAL_DEFAULT_LLM";
	public static final String EMAIL_PROVIDER = "GLOBAL_EMAIL_PROVIDER";
	public static final String EMAIL_API_KEY = "GLOBAL_EMAIL_API_KEY";
	public static final String SENDER_EMAIL = "GLOBAL_SENDER_EMAIL";
	public static final String SENDER_NAME = "GLOBAL_SENDER_NAME";
	public static final String RECIPIENT_EMAIL = "GLOBAL_RECIPIENT_EMAIL";
	public static final String LLM_CACHE_ENABLED = "GLOBAL_LLM_CACHE_ENABLED";
	public static final String LLM_CACHE_TTL_MS = "GLOBAL_LLM_CACHE_TTL_MS";
	public static final String LLM_CACHE_REGENERATE = "GLOBAL_LLM_CACHE_REGENERATE";
	public static final String RAG_ENABLED = "GLOBAL_RAG_ENABLED";
	public static final String DEFAULT_EMBEDDING_MODEL_ID = "GLOBAL_DEFAULT_EMBEDDING_MODEL";
	public static final String DEFAULT_EMBEDDING_STORE_ID = "GLOBAL_DEFAULT_EMBEDDING_STORE";
	public static final String DEFAULT_AI_AGENT_ID = "GLOBAL_DEFAULT_AI_AGENT";
	public static final String SYSTEM_PATH = "/system";
	public static final String GLOBAL_PATH = "/system/global";
	public static final String DECIMAL_SEPARATOR_DEFAULT_VALUE = "DOT";
	public static final String EMAIL_PROVIDER_DEFAULT_VALUE = "BREVO";
	private static final String FALSE_VALUE = "false";

	private final TurConfigVarRepository turConfigVarRepository;

	public TurConfigVarOnStartup(TurConfigVarRepository turConfigVarRepository) {
		this.turConfigVarRepository = turConfigVarRepository;
	}

	public void createDefaultRows() {
		ensureConfigVar(FIRST_TIME, SYSTEM_PATH, "true");
		ensureConfigVar(DECIMAL_SEPARATOR, GLOBAL_PATH, DECIMAL_SEPARATOR_DEFAULT_VALUE);
		ensureConfigVar(PYTHON_EXECUTABLE, GLOBAL_PATH, "");
		ensureConfigVar(PYTHON_REQUIREMENTS, GLOBAL_PATH, "");
		ensureConfigVar(DEFAULT_LLM, GLOBAL_PATH, "");
		ensureConfigVar(EMAIL_PROVIDER, GLOBAL_PATH, EMAIL_PROVIDER_DEFAULT_VALUE);
		ensureConfigVar(EMAIL_API_KEY, GLOBAL_PATH, "");
		ensureConfigVar(SENDER_EMAIL, GLOBAL_PATH, "");
		ensureConfigVar(SENDER_NAME, GLOBAL_PATH, "");
		ensureConfigVar(RECIPIENT_EMAIL, GLOBAL_PATH, "");
		ensureConfigVar(LLM_CACHE_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(LLM_CACHE_TTL_MS, GLOBAL_PATH, "3600000");
		ensureConfigVar(LLM_CACHE_REGENERATE, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(RAG_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(DEFAULT_EMBEDDING_MODEL_ID, GLOBAL_PATH, "");
		ensureConfigVar(DEFAULT_EMBEDDING_STORE_ID, GLOBAL_PATH, "");
		ensureConfigVar(DEFAULT_AI_AGENT_ID, GLOBAL_PATH, "");
	}

	private void ensureConfigVar(String id, String path, String value) {
		if (turConfigVarRepository.findById(id).isEmpty()) {
			TurConfigVar turConfigVar = new TurConfigVar();
			turConfigVar.setId(id);
			turConfigVar.setPath(path);
			turConfigVar.setValue(value);
			turConfigVarRepository.save(turConfigVar);
		}
	}

}
