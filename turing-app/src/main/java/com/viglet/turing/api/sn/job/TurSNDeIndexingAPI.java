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
package com.viglet.turing.api.sn.job;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.json.JSONException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sn/deindex")
@Tag(name = "Semantic Navigation DeIndexing", description = "Semantic Navigation DeIndexing API")
public class TurSNDeIndexingAPI {
	private final TurSNImportAPI turSNImportAPI;
	@Inject
	public TurSNDeIndexingAPI(TurSNImportAPI turSNImportAPI) {
		this.turSNImportAPI = turSNImportAPI;
	}

	@PostMapping
	public String turSNDesIndexingBroker(@RequestBody TurSNJobItems turSNJobItems) {
		turSNImportAPI.send(turSNJobItems);
		return "Ok";

	}
}
