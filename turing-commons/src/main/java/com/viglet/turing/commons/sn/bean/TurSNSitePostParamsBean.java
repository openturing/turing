/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.commons.sn.bean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSitePostParamsBean implements Serializable {
	private String userId;
	private boolean populateMetrics = true;
	private String sort;
	private String query;
	private List<String> fq;
	private List<String> fqAnd;
	private List<String> fqOr;
	private TurSNFilterQueryOperator fqOperator;
	private TurSNFilterQueryOperator fqItemOperator;
	private Integer page;
	private Integer rows;
	private String group;
	private String locale;
	private List<String> fieldList;
	private boolean disableAutoComplete = false;
	private List<String> targetingRules = new ArrayList<>();
	private Map<String, List<String>> targetingRulesWithCondition = new HashMap<>();
	private Map<String, List<String>> targetingRulesWithConditionAND = new HashMap<>();
	private Map<String, List<String>> targetingRulesWithConditionOR = new HashMap<>();
}
