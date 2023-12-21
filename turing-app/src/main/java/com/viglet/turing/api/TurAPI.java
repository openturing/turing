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
package com.viglet.turing.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.json.JSONException;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurAPI {
	@GetMapping
	public Map<String,String> info() throws JSONException {
		Map<String,String> status = new HashMap<>();
		status.put("status", "ok");
		return status;
	}

	@Secured("ROLE_ADMIN")
	@GetMapping("test")
	public Map<String,String> secured() throws JSONException {
		Map<String,String> status = new HashMap<>();
		status.put("status", "secured");
		return status;
	}
}