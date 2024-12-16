/*
 * Copyright (C) 2016-2021 the original author or authors.
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
