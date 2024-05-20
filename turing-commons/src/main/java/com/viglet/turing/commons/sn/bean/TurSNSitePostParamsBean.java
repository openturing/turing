/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.commons.sn.bean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.MapSerializer;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post Parameters for Search with sensitive data
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSitePostParamsBean {
	private String userId;
	private boolean populateMetrics = true;
	private List<String> targetingRules = new ArrayList<>();

	@JsonSerialize(keyUsing = MapSerializer.class)
	private Map<String, List<String>> targetingRulesWithCondition = new HashMap<>();
	@JsonSerialize(keyUsing = MapSerializer.class)
	private Map<String, List<String>> targetingRulesWithConditionAND = new HashMap<>();
	@JsonSerialize(keyUsing = MapSerializer.class)
	private Map<String, List<String>> targetingRulesWithConditionOR = new HashMap<>();
}
