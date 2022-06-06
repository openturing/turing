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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.exchange.TurImportExchange;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/import")
@Tag(name ="Import", description = "Import objects into Viglet Turing")
public class TurImportAPI {

	@Autowired
	private TurImportExchange turImportExchange;

	@PostMapping
	@Transactional
	public TurExchange shImport(@RequestParam("file") MultipartFile multipartFile) {
		return turImportExchange.importFromMultipartFile(multipartFile);
	}
}
