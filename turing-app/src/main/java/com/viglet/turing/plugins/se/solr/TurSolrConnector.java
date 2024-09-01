package com.viglet.turing.plugins.se.solr;

import com.google.inject.Inject;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.plugins.se.TurSeCommons;
import com.viglet.turing.plugins.se.TurSeConnector;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.solr.TurSolrInstanceProcess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@Scope("prototype")
public class TurSolrConnector implements TurSeConnector {
    private final TurSolr turSolr;
    private final TurSeCommons turSeCommons;
    private final TurSolrInstanceProcess turSolrInstanceProcess;

    @Inject
    public TurSolrConnector(TurSolr turSolr,
                            TurSeCommons turSeCommons,
                            TurSolrInstanceProcess turSolrInstanceProcess) {
        this.turSolr = turSolr;
        this.turSeCommons = turSeCommons;
        this.turSolrInstanceProcess = turSolrInstanceProcess;
    }

    @Override
    public TurSNSiteSearchBean snSearch(TurSNSiteSearchContext turSNSiteSearchContext) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteSearchContext.getSiteName(),
                        turSNSiteSearchContext.getLocale())
                .map(turSolrInstance ->
                        turSolr.retrieveSolrFromSN(turSolrInstance, turSNSiteSearchContext)
                                .map(turSEResults ->
                                        turSeCommons.searchResponse(this,
                                                turSNSiteSearchContext,
                                                turSEResults))
                                .orElse(new TurSNSiteSearchBean()))
                .orElse(new TurSNSiteSearchBean());
    }

    @Override
    public TurSEResult findById(TurSNSiteSearchContext turSNSiteSearchContext, TurSNSite turSNSite, String id) {
        return turSolrInstanceProcess.initSolrInstance(turSNSiteSearchContext.getSiteName(),
                        turSNSiteSearchContext.getLocale())
                .map(turSolrInstance -> turSolr.findById(turSolrInstance, turSNSite, id))
                .orElseGet(() -> TurSEResult.builder().build());
    }

    @Override
    public void indexing(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        turSolrInstanceProcess.initSolrInstance(turSNSite.getName(), locale)
                .ifPresent(turSolrInstance ->
                        turSolr.indexing(turSolrInstance, turSNSite, attributes));
    }

}
