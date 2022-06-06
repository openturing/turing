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

import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.thesaurus.TurThesaurusProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.Map;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Component
public class TurSNThesaurusProcess {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
    @Autowired
    private TurThesaurusProcessor turThesaurusProcessor;
    public void processThesaurus(TurSNJobItem turSNJobItem, TurSNSite turSNSite,
                                 Map<String, Object> consolidateResults) {
        boolean thesaurus = false;
        if (turSNSite.getThesaurus() < 1) {
            logger.debug("It is not using Thesaurus to process attributes");
            thesaurus = false;
        } else {
            logger.debug("It is using Thesaurus to process attributes");
            thesaurus = true;
        }
        if (thesaurus) {
            turThesaurusProcessor.startup();
            Map<String, Object> thesaurusResults = turThesaurusProcessor.detectTerms(turSNJobItem.getAttributes());

            logger.debug("thesaurusResults.size(): {}", thesaurusResults.size());
            for (Map.Entry<String, Object> thesaurusResult : thesaurusResults.entrySet()) {
                logger.debug("thesaurusResult Key: {}", thesaurusResult.getKey());
                logger.debug("thesaurusResult Value: {}", thesaurusResult.getValue());
                consolidateResults.put(thesaurusResult.getKey(), thesaurusResult.getValue());
            }
        }
    }
}
