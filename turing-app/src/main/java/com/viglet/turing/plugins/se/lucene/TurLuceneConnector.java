package com.viglet.turing.plugins.se.lucene;

import com.google.inject.Inject;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
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
import com.viglet.turing.se.result.TurSEResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
        return turSNSiteRepository.findByName(context.getSiteName())
                .map(turSNSite -> {
                    List<TurSEResult> turSEResultList = TurLuceneUtils
                            .documentsToSEResults(turLuceneSearch.search(turSEParameters.getQuery()));
                    TurSEResults turSEResults = new TurSEResults(turSEResultList.size(), 0, 20,
                            1, 1, turSEResultList,
                            1, 1, context.getTurSEParameters().getQuery(), "desc",
                            new TurSESpellCheckResult(),
                            null, null,
                            null);
                    return turSeCommons.getSearchBeanForResults(this, context,
                            turSEResults, turSNSite,
                            null);
                })
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
