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
package com.viglet.turing.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;

@Component
public class TurSNSiteFieldUtils {
	private final TurSNSiteFieldRepository turSNSiteFieldRepository;

	@Inject
	public TurSNSiteFieldUtils(TurSNSiteFieldRepository turSNSiteFieldRepository) {
		this.turSNSiteFieldRepository = turSNSiteFieldRepository;
	}

	public Map<String, TurSNSiteField> toMap(TurSNSite turSNSite) {
		List<TurSNSiteField> turSNSiteFields = turSNSiteFieldRepository.findByTurSNSite(turSNSite);
		Map<String, TurSNSiteField> turSNSiteFieldsMap = new HashMap<>();
		for (TurSNSiteField turSNSiteField : turSNSiteFields)
			turSNSiteFieldsMap.put(turSNSiteField.getName(), turSNSiteField);

		return turSNSiteFieldsMap;

	}
}
