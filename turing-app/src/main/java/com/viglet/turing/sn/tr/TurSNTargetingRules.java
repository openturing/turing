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

package com.viglet.turing.sn.tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.stereotype.Component;

@Component
public class TurSNTargetingRules {
	public String run(TurSNTargetingRuleMethod method, String condition,  List<String> trs) {
		return String.format("( %s AND %s )",  condition, this.run(method, trs));
	}

	public String run(TurSNTargetingRuleMethod method, List<String> trs) {
		if (method.equals(TurSNTargetingRuleMethod.AND))
			return this.andMethod(trs);
		else if (method.equals(TurSNTargetingRuleMethod.OR))
			return this.orMethod(trs);
		else
			return "";
	}

	private String andMethod(List<String> trs) {
		// (attribute1:Group OR attribute1:Group2 OR (*:* NOT attribute1:*)) AND
		// (attribute2:Value2 OR (*:* NOT attribute2:*))
		StringBuilder targetingRuleQuery = new StringBuilder();

		Map<String, List<String>> trMap = new HashMap<>();

		for (String tr : trs) {
			if (tr.contains(":")) {
				String attribute = tr.substring(0, tr.indexOf(":"));
				String value = tr.substring(tr.indexOf(":") + 1);
				if (!trMap.containsKey(attribute))
					trMap.put(attribute, new ArrayList<>());
				trMap.get(attribute).add(value);
			}
		}

		String targetingRuleAND = "";

		for (Entry<String, List<String>> trEntry : trMap.entrySet()) {
			String targetingRuleOR = "";
			targetingRuleQuery.append(targetingRuleAND);
			targetingRuleQuery.append("(");
			for (String trEntryValue : trEntry.getValue()) {
				targetingRuleQuery.append(" ");
				targetingRuleQuery
						.append(String.format("%s %s:%s", targetingRuleOR, trEntry.getKey(), trEntryValue).trim());
				targetingRuleOR = "OR";
			}
			targetingRuleQuery.append(String.format(" OR (*:* NOT %s:*)", trEntry.getKey()));
			targetingRuleQuery.append(" )");
			targetingRuleAND = " AND ";
		}

		return targetingRuleQuery.toString();
	}

	private String orMethod(List<String> trs) {
		// This is an example: "(groups:Group1 OR groups:Group2) OR (*:* NOT groups:*)"); //NOSONAR
		List<String> emptyTargetingRules = new ArrayList<>();
		StringBuilder targetingRuleQuery = new StringBuilder();
		targetingRuleQuery.append("(");
		String targetingRuleOR = "";
		for (String tr : trs) {
			targetingRuleQuery.append(targetingRuleOR).append(tr);
			targetingRuleOR = " OR ";
			String[] targetingRuleParts = tr.split(":");
			if (targetingRuleParts.length == 2 && !emptyTargetingRules.contains(targetingRuleParts[0])) {
				emptyTargetingRules.add(targetingRuleParts[0]);
			}
		}
		targetingRuleQuery.append(")");

		if (!emptyTargetingRules.isEmpty()) {
			for (String emptyTargetRule : emptyTargetingRules) {
				targetingRuleQuery.append(String.format(" OR (*:* NOT %s:*)", emptyTargetRule));
			}
		}

		return targetingRuleQuery.toString();
	}
}
