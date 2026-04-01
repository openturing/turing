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

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.exchange.sn.TurSNSiteExportValidator;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/import")
@Tag(name = "Import", description = "Import objects into Viglet Turing")
public class TurImportAPI {

	private final TurImportExchange turImportExchange;
	private final TurSNSiteExportValidator turSNSiteExportValidator;

	public TurImportAPI(TurImportExchange turImportExchange,
			TurSNSiteExportValidator turSNSiteExportValidator) {
		this.turImportExchange = turImportExchange;
		this.turSNSiteExportValidator = turSNSiteExportValidator;
	}

	@PostMapping
	@Transactional
	public TurExchange turImport(@RequestParam("file") MultipartFile multipartFile) {
		return turImportExchange.importFromMultipartFile(multipartFile);
	}

	@PostMapping("/validate")
	public TurSNSiteExportValidator.ValidationResult turValidate(
			@RequestParam("file") MultipartFile multipartFile) {
		TurExchange turExchange = turImportExchange.parseExportFile(multipartFile);
		return turSNSiteExportValidator.validate(turExchange);
	}
}
