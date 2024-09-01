package com.viglet.turing.plugins.se.lucene;

import com.google.inject.Inject;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.lucene.TurLuceneSearch;
import com.viglet.turing.lucene.TurLuceneUtils;
import com.viglet.turing.lucene.TurLuceneWriter;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSeCommons;
import com.viglet.turing.plugins.se.TurSeConnector;
import com.viglet.turing.se.result.TurSEResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@Scope("prototype")
public class TurLuceneConnector implements TurSeConnector {
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurLuceneSearch turLuceneSearch;
    private final TurLuceneWriter turLuceneWriter;
    private final TurSeCommons turSeCommons;
    @Inject
    public TurLuceneConnector(TurSNSiteRepository turSNSiteRepository,
                              TurLuceneSearch turLuceneSearch,
                              TurLuceneWriter turLuceneWriter,
                              TurSeCommons turSeCommons) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turLuceneSearch = turLuceneSearch;
        this.turLuceneWriter = turLuceneWriter;
        this.turSeCommons = turSeCommons;
        log.info("TurLuceneConnector started");
    }


    @Override
    public TurSNSiteSearchBean snSearch(TurSNSiteSearchContext context) {
        TurSEParameters turSEParameters = context.getTurSEParameters();
        System.out.println(turSEParameters);
        return turSNSiteRepository.findByName(context.getSiteName())
                .map(turSNSite -> new TurSNSiteSearchBean()
                        .setResults(turSeCommons.responseDocuments(this, context,
                                turSNSite,
                                null,
                                TurLuceneUtils.documentsToSEResults(turLuceneSearch.search(turSEParameters.getQuery()))))
                        .setPagination(null)
                        .setWidget(null)
                        .setQueryContext(null))
                .orElseGet(TurSNSiteSearchBean::new);
    }

    @Override
    public TurSEResult findById(TurSNSiteSearchContext turSNSiteSearchContext, TurSNSite turSNSite, String id) {
        return null;
    }

    @Override
    public void indexing(TurSNSite turSNSite, Locale locale, Map<String, Object> attributes) {
        turLuceneWriter.indexing(turSNSite, attributes);
    }


}
