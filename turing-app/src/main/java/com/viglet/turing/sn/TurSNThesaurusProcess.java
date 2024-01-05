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
package com.viglet.turing.sn;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.thesaurus.TurThesaurusProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Slf4j
@Component
public class TurSNThesaurusProcess {
    private final TurThesaurusProcessor turThesaurusProcessor;
    @Inject
    public TurSNThesaurusProcess(TurThesaurusProcessor turThesaurusProcessor) {
        this.turThesaurusProcessor = turThesaurusProcessor;
    }

    public void processThesaurus(TurSNJobItem turSNJobItem, TurSNSite turSNSite,
                                 Map<String, Object> consolidateResults) {
        boolean thesaurus = false;
        if (turSNSite.getThesaurus() < 1) {
            log.debug("It is not using Thesaurus to process attributes");
        } else {
            log.debug("It is using Thesaurus to process attributes");
            thesaurus = true;
        }
        if (thesaurus) {
            turThesaurusProcessor.startup();
            Map<String, Object> thesaurusResults = turThesaurusProcessor.detectTerms(turSNJobItem.getAttributes());

            log.debug("thesaurusResults.size(): {}", thesaurusResults.size());
            for (Map.Entry<String, Object> thesaurusResult : thesaurusResults.entrySet()) {
                log.debug("thesaurusResult Key: {}", thesaurusResult.getKey());
                log.debug("thesaurusResult Value: {}", thesaurusResult.getValue());
                consolidateResults.put(thesaurusResult.getKey(), thesaurusResult.getValue());
            }
        }
    }
}
