package com.viglet.turing.plugins.se;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.solr.TurSolrInstance;

import java.util.Locale;
import java.util.Map;

public interface TurSeConnector {
    TurSNSiteSearchBean snSearch(TurSNSiteSearchContext context);
    TurSEResult findById(TurSNSiteSearchContext context, TurSNSite turSNSite, String id);
    void indexing(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes);
}
