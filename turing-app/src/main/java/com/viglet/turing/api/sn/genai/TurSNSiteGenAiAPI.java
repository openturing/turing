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

package com.viglet.turing.api.sn.genai;

import com.google.inject.Inject;
import com.viglet.turing.genai.TurChatMessage;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.genai.TurGenAi;
import com.viglet.turing.genai.TurGenAiContext;
import com.viglet.turing.sn.TurSNSearchProcess;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang.LocaleUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/sn/{siteName}/chat")
@Tag(name = "Semantic Navigation with Generative AI", description = "Semantic Navigation with Generative AI API")
public class TurSNSiteGenAiAPI {
	private final TurSNSearchProcess turSNSearchProcess;
	private final TurGenAi turGenAi;

	@Inject
	public TurSNSiteGenAiAPI(TurSNSearchProcess turSNSearchProcess, TurGenAi turGenAi) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAi = turGenAi;
	}

	@GetMapping
	public TurChatMessage chatMessage(@PathVariable String siteName,
												@RequestParam(name = TurSNParamType.QUERY) String q,
												@RequestParam(required = false, name = TurSNParamType.LOCALE) String localeRequest) {
		Locale locale = LocaleUtils.toLocale(localeRequest);
		if (turSNSearchProcess.existsByTurSNSiteAndLanguage(siteName, locale)) {
			return turSNSearchProcess.getSNSite(siteName).map( site -> {
				TurGenAiContext turGenAiContext = new TurGenAiContext(site.getTurSNSiteGenAi());
				return turGenAi.assistant(turGenAiContext, q);
			}).orElse(TurChatMessage.builder().build());
		}
		return TurChatMessage.builder().build();
	}
}
