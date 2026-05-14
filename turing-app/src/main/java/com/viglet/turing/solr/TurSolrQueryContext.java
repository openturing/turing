package com.viglet.turing.solr;

import java.util.List;
import java.util.Locale;

import org.apache.solr.client.solrj.request.SolrQuery;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

import lombok.Builder;
import lombok.Data;

/**
 * Context object for Solr query execution from Semantic Navigation.
 * Encapsulates all parameters needed for executing and processing Solr queries
 * to reduce method parameter count.
 */
@Data
@Builder
public class TurSolrQueryContext {

    /** Solr query object */
    private SolrQuery query;

    /** Search parameters */
    private TurSEParameters turSEParameters;

    /** MLT (More Like This) field extensions */
    private List<TurSNSiteFieldExt> mltFieldExtList;

    /** Facet field extensions */
    private List<TurSNSiteFieldExt> facetFieldExtList;

    /** Highlighting field extensions */
    private List<TurSNSiteFieldExt> hlFieldExtList;

    /** Spell check result */
    private TurSESpellCheckResult spellCheckResult;

    /** Flag to indicate if query is for rendering facets */
    private boolean queryToRenderFacet;

    /** Locale of the search request, used to resolve localized facet labels. */
    private Locale locale;
}
