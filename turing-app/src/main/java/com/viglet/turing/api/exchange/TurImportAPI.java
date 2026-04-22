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

package com.viglet.turing.api.exchange;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.exchange.sn.TurSNSiteExportValidator;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/import")
@Tag(name = "Import", description = "Import objects into Viglet Turing")
public class TurImportAPI {

	private final TurImportExchange turImportExchange;
	private final TurSNSiteExportValidator turSNSiteExportValidator;
	private final TurSNSiteContentExchangeService contentExchangeService;

	public TurImportAPI(TurImportExchange turImportExchange,
			TurSNSiteExportValidator turSNSiteExportValidator,
			TurSNSiteContentExchangeService contentExchangeService) {
		this.turImportExchange = turImportExchange;
		this.turSNSiteExportValidator = turSNSiteExportValidator;
		this.contentExchangeService = contentExchangeService;
	}

	@PostMapping
	public TurImportExchange.ImportResult turImport(@RequestParam("file") MultipartFile multipartFile,
			@RequestParam(defaultValue = "false") boolean includeContent,
			@RequestParam(defaultValue = "false") boolean includeTemplate,
			@RequestParam(required = false) String taskId,
			@RequestParam(defaultValue = "false") boolean overwrite) {
		return turImportExchange.importFromMultipartFile(
				multipartFile, includeContent, includeTemplate, taskId, overwrite);
	}

	@PostMapping("/check-content")
	public Map<String, Object> checkContent(@RequestParam("file") MultipartFile multipartFile) {
		boolean hasContent = turImportExchange.hasContentFile(multipartFile);
		return Map.of("hasContent", hasContent);
	}

	@PostMapping("/check-zip")
	public Map<String, Object> checkZip(@RequestParam("file") MultipartFile multipartFile) {
		return turImportExchange.checkZip(multipartFile);
	}

	@GetMapping(value = "/progress/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter importProgress(@PathVariable String taskId) {
		SseEmitter emitter = new SseEmitter(600_000L);
		Thread.ofVirtual().start(() -> {
			try {
				boolean completed = false;
				while (!completed) {
					var progress = contentExchangeService.getProgress(taskId);
					if (progress.isPresent()) {
						var p = progress.get();
						emitter.send(SseEmitter.event()
								.name("progress")
								.data(Map.of(
										"totalDocuments", p.totalDocuments(),
										"processedDocuments", p.processedDocuments(),
										"percentage", p.percentage(),
										"currentLocale", p.currentLocale(),
										"phase", p.phase(),
										"estimatedRemainingMillis", p.estimatedRemainingMillis()
								)));
						if ("completed".equals(p.phase())) {
							completed = true;
							contentExchangeService.removeProgress(taskId);
						}
					}
					if (!completed) {
						Thread.sleep(500);
					}
				}
				emitter.complete();
			} catch (Exception e) {
				emitter.completeWithError(e);
			}
		});
		return emitter;
	}

	@PostMapping("/validate")
	public TurSNSiteExportValidator.ValidationResult turValidate(
			@RequestParam("file") MultipartFile multipartFile) {
		TurExchange turExchange = turImportExchange.parseExportFile(multipartFile);
		return turSNSiteExportValidator.validate(turExchange);
	}
}
