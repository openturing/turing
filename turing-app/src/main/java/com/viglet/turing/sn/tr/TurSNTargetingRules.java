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

import com.viglet.turing.solr.TurSolrUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.Map.Entry;

@Component
public class TurSNTargetingRules {

    public static final String TWO_POINTS = ":";
    public static final String EMPTY = "";

    public String ruleExpression(TurSNTargetingRuleMethod method, List<String> trs) {
        if (method.equals(TurSNTargetingRuleMethod.AND))
            return this.andMethod(trs);
        else if (method.equals(TurSNTargetingRuleMethod.OR))
            return this.orMethod(trs);
        else
            return EMPTY;
    }

    public String andMethod(List<String> trs) {
        // (attribute1:Group OR attribute1:Group2 OR (*:* NOT attribute1:*)) AND
        // (attribute2:Value2 OR (*:* NOT attribute2:*))
        StringBuilder targetingRuleQuery = new StringBuilder();

        Map<String, List<String>> trMap = new HashMap<>();

        trs.stream().filter(tr -> tr.contains(TWO_POINTS)).forEach(tr -> {
            String attribute = tr.substring(0, tr.indexOf(TWO_POINTS));
            String value = tr.substring(tr.indexOf(TWO_POINTS) + 1);
            trMap.computeIfAbsent(attribute, k -> trMap.put(k, new ArrayList<>()));
            trMap.get(attribute).add(value);
        });

        String targetingRuleAND = EMPTY;

        for (Entry<String, List<String>> trEntry : trMap.entrySet()) {
            String targetingRuleOR = EMPTY;
            targetingRuleQuery.append(targetingRuleAND);
            targetingRuleQuery.append("(");
            for (String trEntryValue : trEntry.getValue()) {
                targetingRuleQuery.append(" ");
                targetingRuleQuery
                        .append("%s %s:%s".formatted(targetingRuleOR, trEntry.getKey(), trEntryValue).trim());
                targetingRuleOR = "OR";
            }
            targetingRuleQuery.append(" OR (*:* NOT %s:*)".formatted(trEntry.getKey()));
            targetingRuleQuery.append(" )");
            targetingRuleAND = " AND ";
        }

        return targetingRuleQuery.toString();
    }

    public String orMethod(List<String> trs) {
        // (+
        //        attribute1:"Value1"+
        //      OR+
        //        attribute2:"Value2"+
        //      OR+
        //        (*:*+NOT+attribute1:*+AND+NOT+attribute2:*)+
        // )
        Set<String> trList = new HashSet<>();
        Set<String> attributeList = new HashSet<>();
        trs.stream().filter(tr -> tr.contains(TWO_POINTS))
                .forEach(tr ->
                        TurSolrUtils.getQueryKeyValue(tr)
                                .ifPresent(kv -> {
                                    attributeList.add(String.format("NOT %s:*", kv.getKey()));
                                   trList.add(String.format("%s:%s",kv.getKey(), kv.getValue()));
                                }));
        return String.format("%s OR (*:* %s)",
                String.join(" OR ", trList),
                String.join(" AND ", attributeList));
    }
}
