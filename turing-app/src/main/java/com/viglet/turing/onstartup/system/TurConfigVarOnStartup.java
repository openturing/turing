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

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Component
@Transactional
public class TurConfigVarOnStartup {
	/** Reused across calls — SecureRandom is thread-safe and instantiating it is expensive (kernel entropy seeding). */
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	public static final String FIRST_TIME = "FIRST_TIME";
	public static final String DECIMAL_SEPARATOR = "GLOBAL_DECIMAL_SEPARATOR";
	public static final String PYTHON_EXECUTABLE = "GLOBAL_PYTHON_EXECUTABLE";
	public static final String PYTHON_REQUIREMENTS = "GLOBAL_PYTHON_REQUIREMENTS";
	/**
	 * Server-side HMAC secret used by
	 * {@code TurCodeInterpreterUrlSigner} to sign / verify the
	 * {@code ?exp&sig} query string on {@code /api/v2/code-interpreter/}
	 * URLs. Auto-generated with {@link SecureRandom} on first boot (32
	 * random bytes, Base64-encoded) so a deployment without explicit
	 * configuration still gets unguessable signed URLs. Operators may
	 * rotate by deleting the row — the next startup mints a fresh secret
	 * (existing URLs invalidate, which is the desired post-rotation
	 * outcome).
	 */
	public static final String CODE_INTERPRETER_URL_SIGNING_SECRET =
			"GLOBAL_CODE_INTERPRETER_URL_SIGNING_SECRET";
	/**
	 * T80 — Code Interpreter execution mode: {@code NATIVE} (default;
	 * host subprocess, legacy behavior) or {@code DOCKER} (each execution
	 * runs in a throwaway hardened container). Operator choice, so a
	 * deployment can opt into containerization without a code change.
	 */
	public static final String CODE_INTERPRETER_EXECUTION_MODE =
			"GLOBAL_CODE_INTERPRETER_EXECUTION_MODE";
	public static final String CODE_INTERPRETER_EXECUTION_MODE_DEFAULT_VALUE = "NATIVE";
	/**
	 * T80 — Docker image used when {@link #CODE_INTERPRETER_EXECUTION_MODE}
	 * is {@code DOCKER}. Should bundle Python plus the packages the agents
	 * rely on (reportlab, matplotlib, qrcode, …). Defaults to the upstream
	 * slim Python image; operators typically publish their own.
	 */
	public static final String CODE_INTERPRETER_DOCKER_IMAGE =
			"GLOBAL_CODE_INTERPRETER_DOCKER_IMAGE";
	public static final String CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE = "python:3.12-slim";
	/**
	 * T321 — Docker image for the Anthropic-compatible <em>skill</em> sandbox
	 * session. Unlike the one-shot snippet image
	 * ({@link #CODE_INTERPRETER_DOCKER_IMAGE}), a skill session runs arbitrary
	 * {@code bash} the model drives across turns, so the image should match
	 * Anthropic's skill container — <b>python + node + bash</b>. The default is
	 * the upstream slim Python image (python + bash, no node); operators that
	 * need node (or extra packages) publish their own and point this setting at
	 * it. Used only when the execution mode is {@code DOCKER} — the skill
	 * sandbox is gated on containerization.
	 */
	public static final String CODE_INTERPRETER_SKILL_IMAGE =
			"GLOBAL_CODE_INTERPRETER_SKILL_IMAGE";
	public static final String CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE = "python:3.12-slim";
	public static final String DEFAULT_LLM = "GLOBAL_DEFAULT_LLM";
	/**
	 * T517 — cross-provider per-stage model "lanes". Each holds an
	 * {@code TurLLMInstance} id the matching pipeline stages use instead of the
	 * default LLM; blank = fall back to {@link #DEFAULT_LLM} (legacy behaviour).
	 */
	public static final String MODEL_LANE_FAST = "GLOBAL_MODEL_LANE_FAST";
	public static final String MODEL_LANE_REASONING = "GLOBAL_MODEL_LANE_REASONING";
	public static final String MODEL_LANE_CHEAP = "GLOBAL_MODEL_LANE_CHEAP";
	/**
	 * T518 — cost-aware cross-provider fallback chain: a comma-separated, ordered
	 * list of {@code TurLLMInstance} ids the meta-provider fails over to after the
	 * primary. Blank = no chain (legacy single-model path).
	 */
	public static final String LLM_FALLBACK_CHAIN = "GLOBAL_LLM_FALLBACK_CHAIN";
	/** T518 — fallback routing mode: {@code PRIORITY} (default) | {@code CHEAPEST}. */
	public static final String LLM_FALLBACK_MODE = "GLOBAL_LLM_FALLBACK_MODE";
	public static final String LLM_FALLBACK_MODE_DEFAULT_VALUE = "PRIORITY";
	/**
	 * T522 — multi-provider "second opinion": when enabled, a different-vendor
	 * critic LLM judges the primary RAG answer and the agreement is surfaced as a
	 * confidence signal. Default off (no extra call).
	 */
	public static final String SECOND_OPINION_ENABLED = "GLOBAL_SECOND_OPINION_ENABLED";
	/** T522 — the critic {@code TurLLMInstance} id (should be a different vendor). */
	public static final String SECOND_OPINION_LLM = "GLOBAL_SECOND_OPINION_LLM";
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
	/** T61 — PII slot retention TTL in hours; 0/negative disables cleanup. */
	public static final String PII_SLOT_TTL_HOURS = "GLOBAL_PII_SLOT_TTL_HOURS";
	public static final String PII_SLOT_TTL_HOURS_DEFAULT_VALUE = "24";
	/**
	 * T328 — relevance floor (cosine similarity) applied on the SN RAG
	 * <em>filtered</em> retrieval path, replacing the hard-coded {@code 0.0}.
	 * Default {@code 0.0} preserves legacy behavior (stuff every filter-matching
	 * hit); raise it to drop off-topic-but-filter-matching chunks before they
	 * reach the model. The unfiltered path keeps its own {@code 0.4} floor.
	 */
	public static final String RAG_SN_SIMILARITY_THRESHOLD = "GLOBAL_RAG_SN_SIMILARITY_THRESHOLD";
	public static final String RAG_SN_SIMILARITY_THRESHOLD_DEFAULT_VALUE = "0.0";
	/** T328 — opt-in LLM-as-reranker over SN RAG candidates (default off). */
	public static final String RAG_SN_RERANK_ENABLED = "GLOBAL_RAG_SN_RERANK_ENABLED";
	/**
	 * T328 — max chunks kept after the reranker narrows the retrieved candidate
	 * pool (the stuffed top-k). Only consulted when {@link #RAG_SN_RERANK_ENABLED}
	 * is on; the candidate pool itself stays the path's normal retrieval topK.
	 */
	public static final String RAG_SN_RERANK_TOP_N = "GLOBAL_RAG_SN_RERANK_TOP_N";
	public static final String RAG_SN_RERANK_TOP_N_DEFAULT_VALUE = "20";
	/**
	 * T337 — which reranker backend runs when {@link #RAG_SN_RERANK_ENABLED} is
	 * on: {@code LLM} (legacy default) | {@code CROSS_ENCODER} | {@code COHERE}.
	 * An unknown value falls back to {@code LLM}.
	 */
	public static final String RAG_SN_RERANK_STRATEGY = "GLOBAL_RAG_SN_RERANK_STRATEGY";
	public static final String RAG_SN_RERANK_STRATEGY_DEFAULT_VALUE = "LLM";
	/** T338 — self-hosted cross-encoder {@code /rerank} endpoint URL (CROSS_ENCODER). */
	public static final String RAG_SN_RERANK_ENDPOINT = "GLOBAL_RAG_SN_RERANK_ENDPOINT";
	/** T338 — rerank model name (cross-encoder / Cohere); blank uses the backend default. */
	public static final String RAG_SN_RERANK_MODEL = "GLOBAL_RAG_SN_RERANK_MODEL";
	/** T521 — AWS region for the managed Bedrock Rerank API ({@code BEDROCK}). */
	public static final String RAG_SN_RERANK_REGION = "GLOBAL_RAG_SN_RERANK_REGION";
	public static final String RAG_SN_RERANK_REGION_DEFAULT_VALUE = "us-east-1";
	/** T521 — GCP project for the managed Vertex AI Ranking API ({@code VERTEX_AI}). */
	public static final String RAG_SN_RERANK_VERTEX_PROJECT = "GLOBAL_RAG_SN_RERANK_VERTEX_PROJECT";
	/** T521 — GCP location for Vertex AI Ranking (default {@code global}). */
	public static final String RAG_SN_RERANK_VERTEX_LOCATION = "GLOBAL_RAG_SN_RERANK_VERTEX_LOCATION";
	public static final String RAG_SN_RERANK_VERTEX_LOCATION_DEFAULT_VALUE = "global";
	/** T339 — Cohere API key, stored encrypted; required only for the COHERE strategy. */
	public static final String RAG_SN_RERANK_API_KEY = "GLOBAL_RAG_SN_RERANK_API_KEY";
	/**
	 * T341 — opt-in memoization of reranker results in the {@code turRagRerankScore}
	 * cache (default off). A rerank ordering is deterministic per
	 * {@code (strategy, model, query, candidate-set)}; enabling this skips repeat
	 * scoring of an identical query+pool. Only worth it when profiling shows
	 * repeated identical rerank calls in production.
	 */
	public static final String RAG_SN_RERANK_CACHE_ENABLED = "GLOBAL_RAG_SN_RERANK_CACHE_ENABLED";
	/**
	 * T331 — opt-in generation of 2–3 grounded follow-up questions after each SN
	 * AI-mode answer (rendered as clickable chips). Default off (one extra cheap
	 * LLM call per turn).
	 */
	public static final String RAG_SN_FOLLOWUPS_ENABLED = "GLOBAL_RAG_SN_FOLLOWUPS_ENABLED";
	/**
	 * T330 — opt-in post-generation groundedness (faithfulness) audit for SN
	 * answers: a cheap LLM call checks the answer is supported by the retrieved
	 * chunks and, on fail, appends a localized low-confidence caveat. Default off
	 * (one extra LLM round-trip per turn).
	 */
	public static final String RAG_SN_GROUNDEDNESS_CHECK_ENABLED =
			"GLOBAL_RAG_SN_GROUNDEDNESS_CHECK_ENABLED";
	/**
	 * T687 / §XLII.1 — active speech-to-text backend: {@code OPENAI} (legacy
	 * default, piggybacks the default LLM instance) | {@code OPENAI_COMPATIBLE}
	 * (dedicated self-hosted endpoint) | {@code NONE} (disabled). Unknown values
	 * fall back to {@code OPENAI}.
	 */
	public static final String TRANSCRIPTION_STRATEGY = "GLOBAL_TRANSCRIPTION_STRATEGY";
	public static final String TRANSCRIPTION_STRATEGY_DEFAULT_VALUE = "OPENAI";
	/** T687 — dedicated OpenAI-compatible {@code /audio/transcriptions} base URL; blank falls back to the default LLM instance URL. */
	public static final String TRANSCRIPTION_ENDPOINT = "GLOBAL_TRANSCRIPTION_ENDPOINT";
	/** T687 — transcription model name; blank uses the backend default (e.g. {@code whisper-1}). */
	public static final String TRANSCRIPTION_MODEL = "GLOBAL_TRANSCRIPTION_MODEL";
	/** T687 — transcription API key, stored encrypted; blank falls back to the default LLM instance key. */
	public static final String TRANSCRIPTION_API_KEY = "GLOBAL_TRANSCRIPTION_API_KEY";
	/**
	 * T687 — per-request upload limit in bytes for the active backend. Audio
	 * above this is chunked (T688/T689). OpenAI's endpoint hard-caps at 25 MiB
	 * (26,214,400); a local server can be much larger.
	 */
	public static final String TRANSCRIPTION_MAX_UPLOAD_BYTES = "GLOBAL_TRANSCRIPTION_MAX_UPLOAD_BYTES";
	public static final String TRANSCRIPTION_MAX_UPLOAD_BYTES_DEFAULT_VALUE = "26214400";
	/**
	 * T739 / §XLVIII — URL content-fetch mode: {@code SIMPLE} (legacy
	 * HttpURLConnection+Tika, default) | {@code HEADLESS} (always render in the
	 * browserless sidecar) | {@code AUTO} (SIMPLE, escalate to HEADLESS for
	 * JS-rendered SPAs). Unknown values fall back to {@code SIMPLE}.
	 */
	public static final String URL_FETCH_MODE = "GLOBAL_URL_FETCH_MODE";
	public static final String URL_FETCH_MODE_DEFAULT_VALUE = "SIMPLE";
	/** T739 — base URL of the {@code browserless/chromium} sidecar (e.g. {@code http://browserless:3000}); blank = no sidecar. */
	public static final String URL_FETCH_BROWSERLESS_URL = "GLOBAL_URL_FETCH_BROWSERLESS_URL";
	/** T739 — optional browserless auth token, stored encrypted; blank = none. */
	public static final String URL_FETCH_BROWSERLESS_TOKEN = "GLOBAL_URL_FETCH_BROWSERLESS_TOKEN";
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
		// Auto-heal variant for the HMAC secret: like ensureConfigVar but
		// also re-mints when the row exists with a blank value. Plain
		// ensureConfigVar wouldn't fix dev/staging DBs that ended up with
		// the row but no value (e.g. a snapshot from an earlier build
		// before the random-bytes generator was wired). On HEALTHY rows
		// (existing + non-blank), this is a no-op — fleet-wide signed URLs
		// stay valid across restarts.
		ensureSecretConfigVar(CODE_INTERPRETER_URL_SIGNING_SECRET, GLOBAL_PATH);
		ensureConfigVar(CODE_INTERPRETER_EXECUTION_MODE, GLOBAL_PATH,
				CODE_INTERPRETER_EXECUTION_MODE_DEFAULT_VALUE);
		ensureConfigVar(CODE_INTERPRETER_DOCKER_IMAGE, GLOBAL_PATH,
				CODE_INTERPRETER_DOCKER_IMAGE_DEFAULT_VALUE);
		ensureConfigVar(CODE_INTERPRETER_SKILL_IMAGE, GLOBAL_PATH,
				CODE_INTERPRETER_SKILL_IMAGE_DEFAULT_VALUE);
		ensureConfigVar(DEFAULT_LLM, GLOBAL_PATH, "");
		ensureConfigVar(MODEL_LANE_FAST, GLOBAL_PATH, "");
		ensureConfigVar(MODEL_LANE_REASONING, GLOBAL_PATH, "");
		ensureConfigVar(MODEL_LANE_CHEAP, GLOBAL_PATH, "");
		ensureConfigVar(LLM_FALLBACK_CHAIN, GLOBAL_PATH, "");
		ensureConfigVar(LLM_FALLBACK_MODE, GLOBAL_PATH, LLM_FALLBACK_MODE_DEFAULT_VALUE);
		ensureConfigVar(SECOND_OPINION_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(SECOND_OPINION_LLM, GLOBAL_PATH, "");
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
		ensureConfigVar(PII_SLOT_TTL_HOURS, GLOBAL_PATH, PII_SLOT_TTL_HOURS_DEFAULT_VALUE);
		ensureConfigVar(RAG_SN_SIMILARITY_THRESHOLD, GLOBAL_PATH,
				RAG_SN_SIMILARITY_THRESHOLD_DEFAULT_VALUE);
		ensureConfigVar(RAG_SN_RERANK_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(RAG_SN_RERANK_TOP_N, GLOBAL_PATH, RAG_SN_RERANK_TOP_N_DEFAULT_VALUE);
		ensureConfigVar(RAG_SN_RERANK_STRATEGY, GLOBAL_PATH, RAG_SN_RERANK_STRATEGY_DEFAULT_VALUE);
		ensureConfigVar(RAG_SN_RERANK_ENDPOINT, GLOBAL_PATH, "");
		ensureConfigVar(RAG_SN_RERANK_MODEL, GLOBAL_PATH, "");
		ensureConfigVar(RAG_SN_RERANK_REGION, GLOBAL_PATH, RAG_SN_RERANK_REGION_DEFAULT_VALUE);
		ensureConfigVar(RAG_SN_RERANK_VERTEX_PROJECT, GLOBAL_PATH, "");
		ensureConfigVar(RAG_SN_RERANK_VERTEX_LOCATION, GLOBAL_PATH,
				RAG_SN_RERANK_VERTEX_LOCATION_DEFAULT_VALUE);
		ensureConfigVar(RAG_SN_RERANK_API_KEY, GLOBAL_PATH, "");
		ensureConfigVar(RAG_SN_RERANK_CACHE_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(RAG_SN_FOLLOWUPS_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(RAG_SN_GROUNDEDNESS_CHECK_ENABLED, GLOBAL_PATH, FALSE_VALUE);
		ensureConfigVar(TRANSCRIPTION_STRATEGY, GLOBAL_PATH, TRANSCRIPTION_STRATEGY_DEFAULT_VALUE);
		ensureConfigVar(TRANSCRIPTION_ENDPOINT, GLOBAL_PATH, "");
		ensureConfigVar(TRANSCRIPTION_MODEL, GLOBAL_PATH, "");
		ensureConfigVar(TRANSCRIPTION_API_KEY, GLOBAL_PATH, "");
		ensureConfigVar(TRANSCRIPTION_MAX_UPLOAD_BYTES, GLOBAL_PATH,
				TRANSCRIPTION_MAX_UPLOAD_BYTES_DEFAULT_VALUE);
		ensureConfigVar(URL_FETCH_MODE, GLOBAL_PATH, URL_FETCH_MODE_DEFAULT_VALUE);
		ensureConfigVar(URL_FETCH_BROWSERLESS_URL, GLOBAL_PATH, "");
		ensureConfigVar(URL_FETCH_BROWSERLESS_TOKEN, GLOBAL_PATH, "");
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

	/**
	 * Like {@link #ensureConfigVar}, but also re-mints the value when the
	 * row exists with a blank/null value. Used only for the HMAC URL-
	 * signing secret — there's no legitimate "the operator deliberately
	 * left this blank" state for a cryptographic secret, so a blank means
	 * something went wrong upstream and we should self-heal. For ordinary
	 * config rows, plain {@link #ensureConfigVar} is the right tool —
	 * empty strings are valid (e.g. {@code GLOBAL_PYTHON_REQUIREMENTS}
	 * defaults to {@code ""}).
	 */
	private void ensureSecretConfigVar(String id, String path) {
		TurConfigVar existing = turConfigVarRepository.findById(id).orElse(null);
		if (existing == null) {
			TurConfigVar fresh = new TurConfigVar();
			fresh.setId(id);
			fresh.setPath(path);
			fresh.setValue(generateUrlSigningSecret());
			turConfigVarRepository.save(fresh);
			return;
		}
		if (existing.getValue() == null || existing.getValue().isBlank()) {
			existing.setValue(generateUrlSigningSecret());
			turConfigVarRepository.save(existing);
		}
	}

	/**
	 * 32 random bytes → 44-char Base64 string. 32 bytes = 256 bits, matches
	 * HMAC-SHA256's native block size. Re-used by:
	 * <ul>
	 *   <li>{@link #ensureSecretConfigVar} on fresh install / auto-heal of
	 *       a blank row;</li>
	 *   <li>{@code TurGlobalSettingsService.regenerateCodeInterpreterUrlSigningSecret()}
	 *       when an admin clicks "Regenerate" in the Global Settings page.</li>
	 * </ul>
	 * Public so the service in {@code com.viglet.turing.system} can call it
	 * across the package boundary.
	 */
	public static String generateUrlSigningSecret() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

}
