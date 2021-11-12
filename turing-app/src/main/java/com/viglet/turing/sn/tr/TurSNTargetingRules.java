/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
			String[] targetingRuleParts = tr.split(":");
			if (targetingRuleParts.length == 2) {
				String attribute = targetingRuleParts[0].trim();
				String value = targetingRuleParts[1];
				trMap.computeIfAbsent(attribute, k -> trMap.put(attribute, new ArrayList<>()));
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
		// Sample: "(groups:Group1 OR groups:Group2) OR (*:* NOT groups:*)");
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
