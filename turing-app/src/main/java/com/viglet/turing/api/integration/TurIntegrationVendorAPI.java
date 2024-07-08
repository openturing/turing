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
package com.viglet.turing.api.integration;

import com.viglet.turing.persistence.bean.integration.TurIntegrationVendor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integration/vendor")
@Tag(name = "Integration Vendor", description = "Integration Vendor API")
public class TurIntegrationVendorAPI {

	@Operation(summary = "Integration Vendor List")
	@GetMapping
	public List<TurIntegrationVendor> turIntegrationVendorList() {
		return List.of(new TurIntegrationVendor("AEM", "AEM"),
				new TurIntegrationVendor("WEB-CRAWLER", "Web Crawler"));
	}


}
