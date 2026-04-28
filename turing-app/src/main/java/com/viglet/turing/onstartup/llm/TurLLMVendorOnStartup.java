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
package com.viglet.turing.onstartup.llm;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

@Component
@Transactional
public class TurLLMVendorOnStartup {

	private final TurLLMVendorRepository turLLMVendorRepository;

	public TurLLMVendorOnStartup(TurLLMVendorRepository turLLMVendorRepository) {
		this.turLLMVendorRepository = turLLMVendorRepository;
	}

	public void createDefaultRows() {

		if (turLLMVendorRepository.findAll().isEmpty()) {
			TurLLMVendor openai = new TurLLMVendor();
			openai.setId("OPENAI");
			openai.setDescription("Open AI");
			openai.setPlugin("openai");
			openai.setTitle("Open AI");
			openai.setWebsite("https://openai.com");
			turLLMVendorRepository.save(openai);

			TurLLMVendor ollama = new TurLLMVendor();
			ollama.setId("OLLAMA");
			ollama.setDescription("Ollama");
			ollama.setPlugin("ollama");
			ollama.setTitle("Ollama");
			ollama.setWebsite("https://ollama.com");
			turLLMVendorRepository.save(ollama);

			TurLLMVendor anthropic = new TurLLMVendor();
			anthropic.setId("ANTHROPIC");
			anthropic.setDescription("Anthropic (Claude)");
			anthropic.setPlugin("anthropic");
			anthropic.setTitle("Anthropic");
			anthropic.setWebsite("https://anthropic.com");
			turLLMVendorRepository.save(anthropic);

			TurLLMVendor gemini = new TurLLMVendor();
			gemini.setId("GEMINI");
			gemini.setDescription("Google Gemini (native GenAI SDK)");
			gemini.setPlugin("gemini");
			gemini.setTitle("Google Gemini");
			gemini.setWebsite("https://ai.google.dev");
			turLLMVendorRepository.save(gemini);

			TurLLMVendor geminiOpenAI = new TurLLMVendor();
			geminiOpenAI.setId("GEMINI_OPENAI");
			geminiOpenAI.setDescription("Google Gemini via OpenAI-compatible endpoint");
			geminiOpenAI.setPlugin("gemini-openai");
			geminiOpenAI.setTitle("Google Gemini (OpenAI Compatible)");
			geminiOpenAI.setWebsite("https://ai.google.dev");
			turLLMVendorRepository.save(geminiOpenAI);

		}
	}
}
