package com.viglet.turing.plugins.se;

import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.solr.TurSolrInstance;

import java.util.Optional;

public interface TurSePlugin {
    public Optional<TurSEResults> retrieveFromSn(TurSeContext seContext,
                                                     TurSNSiteSearchContext context);
}
