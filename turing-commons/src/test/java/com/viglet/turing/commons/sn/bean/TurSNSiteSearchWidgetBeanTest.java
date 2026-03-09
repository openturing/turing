package com.viglet.turing.commons.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;

class TurSNSiteSearchWidgetBeanTest {

    @Test
    void shouldStoreWidgetSections() {
        TurSNSiteSearchWidgetBean bean = new TurSNSiteSearchWidgetBean();
        TurSNSiteSearchFacetBean facet = new TurSNSiteSearchFacetBean();
        TurSESimilarResult similar = TurSESimilarResult.builder().id("1").build();
        TurSNSiteSpellCheckBean spellCheck = new TurSNSiteSpellCheckBean();
        TurSNSiteLocaleBean locale = new TurSNSiteLocaleBean();
        TurSNSiteSpotlightDocumentBean spotlight = new TurSNSiteSpotlightDocumentBean();

        bean.setCleanUpFacets("/search?clear=1");
        bean.setSelectedFilterQueries(List.of("type:article"));
        bean.setFacet(List.of(facet));
        bean.setSecondaryFacet(List.of(facet));
        bean.setFacetToRemove(facet);
        bean.setSimilar(List.of(similar));
        bean.setSpellCheck(spellCheck);
        bean.setLocales(List.of(locale));
        bean.setSpotlights(List.of(spotlight));

        assertThat(bean.getCleanUpFacets()).contains("clear=1");
        assertThat(bean.getSelectedFilterQueries()).containsExactly("type:article");
        assertThat(bean.getFacet()).containsExactly(facet);
        assertThat(bean.getSecondaryFacet()).containsExactly(facet);
        assertThat(bean.getFacetToRemove()).isSameAs(facet);
        assertThat(bean.getSimilar()).containsExactly(similar);
        assertThat(bean.getSpellCheck()).isSameAs(spellCheck);
        assertThat(bean.getLocales()).containsExactly(locale);
        assertThat(bean.getSpotlights()).containsExactly(spotlight);
    }
}
