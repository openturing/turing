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

import com.viglet.turing.genai.provider.llm.TurHuggingFaceEmbeddingModelFactory;
import com.viglet.turing.genai.provider.llm.TurLocalEmbeddingModelFactory;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

@Component
@Transactional
public class TurLLMVendorOnStartup {

	private final TurLLMVendorRepository turLLMVendorRepository;

	public TurLLMVendorOnStartup(TurLLMVendorRepository turLLMVendorRepository) {
		this.turLLMVendorRepository = turLLMVendorRepository;
	}

	/**
	 * Seeds the built-in LLM vendors, idempotently per vendor (T754). Was
	 * all-or-nothing on an empty table, which meant a new vendor added later never
	 * reached existing installs; now each vendor is created only if missing, so the
	 * set self-heals on upgrade. Never overwrites an existing (possibly
	 * user-edited) row.
	 */
	public void createDefaultRows() {
		ensureVendor("OPENAI", "Open AI", "openai", "Open AI", "https://openai.com");
		ensureVendor("OLLAMA", "Ollama", "ollama", "Ollama", "https://ollama.com");
		ensureVendor("ANTHROPIC", "Anthropic (Claude)", "anthropic", "Anthropic", "https://anthropic.com");
		ensureVendor("GEMINI", "Google Gemini (native GenAI SDK)", "gemini", "Google Gemini",
				"https://ai.google.dev");
		ensureVendor("GEMINI_OPENAI", "Google Gemini via OpenAI-compatible endpoint", "gemini-openai",
				"Google Gemini (OpenAI Compatible)", "https://ai.google.dev");
		ensureVendor("OPENAI_COMPAT",
				"Any OpenAI-compatible endpoint (DeepSeek, xAI Grok, Groq, Cerebras, OpenRouter, "
						+ "Together, Fireworks, local vLLM/LM-Studio) — set the base URL",
				"openai-compatible", "OpenAI-Compatible", "https://platform.openai.com/docs/api-reference");
		ensureVendor("BEDROCK",
				"AWS Bedrock — Claude/Llama/Mistral/Nova/Titan on one IAM-authenticated gateway", "bedrock",
				"AWS Bedrock", "https://aws.amazon.com/bedrock");
		ensureVendor("VOYAGE", "Voyage AI — retrieval-specialist embeddings + rerank (no chat API)", "voyage",
				"Voyage AI", "https://www.voyageai.com");
		ensureVendor("COHERE", "Cohere — Command chat + Embed v4 (OpenAI-compatible endpoint)", "cohere",
				"Cohere", "https://cohere.com");
		ensureVendor("MISTRAL", "Mistral AI — European/GDPR-native chat + embeddings (OpenAI-compatible)",
				"mistral", "Mistral AI", "https://mistral.ai");
		ensureVendor("VERTEX_AI",
				"Google Vertex AI — enterprise Gemini on GCP (IAM/VPC-SC/CMEK, regional endpoints)",
				"vertex-ai", "Google Vertex AI", "https://cloud.google.com/vertex-ai");
		// T754 / ADR 0004 — the two in-process ONNX embedding modes become first-class
		// vendors (embedding-only, no cloud, no chat). The id equals the embedding
		// factory PROVIDER_TYPE so the in-process factory resolves by vendor.
		ensureVendor(TurLocalEmbeddingModelFactory.PROVIDER_TYPE,
				"In-process ONNX embedding model — local .onnx + tokenizer (no cloud, no chat)",
				"transformers-local", "Local ONNX (Transformers)", "https://onnxruntime.ai");
		ensureVendor(TurHuggingFaceEmbeddingModelFactory.PROVIDER_TYPE,
				"In-process ONNX embedding model from a Hugging Face repo id (no cloud, no chat)",
				"huggingface", "Hugging Face (local ONNX)", "https://huggingface.co");
	}

	/** Creates the vendor only when its id is not already present (idempotent). */
	private void ensureVendor(String id, String description, String plugin, String title, String website) {
		if (turLLMVendorRepository.existsById(id)) {
			return;
		}
		TurLLMVendor vendor = new TurLLMVendor();
		vendor.setId(id);
		vendor.setDescription(description);
		vendor.setPlugin(plugin);
		vendor.setTitle(title);
		vendor.setWebsite(website);
		turLLMVendorRepository.save(vendor);
	}
}
