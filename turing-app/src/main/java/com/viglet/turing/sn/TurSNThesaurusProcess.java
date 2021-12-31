package com.viglet.turing.sn;

import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.thesaurus.TurThesaurusProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TurSNThesaurusProcess {
    private static final Logger logger = LogManager.getLogger(TurSNThesaurusProcess.class);
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
