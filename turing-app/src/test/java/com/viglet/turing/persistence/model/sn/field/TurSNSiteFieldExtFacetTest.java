package com.viglet.turing.persistence.model.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurSNSiteFieldExtFacetTest {

    private TurSNSiteFieldExtFacet facet;

    @BeforeEach
    void setUp() {
        facet = new TurSNSiteFieldExtFacet();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();

        facet.setId("facet-id");
        facet.setLocale(Locale.US);
        facet.setLabel("Category");
        facet.setTurSNSiteFieldExt(fieldExt);

        assertThat(facet.getId()).isEqualTo("facet-id");
        assertThat(facet.getLocale()).isEqualTo(Locale.US);
        assertThat(facet.getLabel()).isEqualTo("Category");
        assertThat(facet.getTurSNSiteFieldExt()).isEqualTo(fieldExt);
    }

    @Test
    void shouldCreateUsingBuilder() {
        TurSNSiteFieldExt fieldExt = new TurSNSiteFieldExt();

        TurSNSiteFieldExtFacet built = TurSNSiteFieldExtFacet.builder()
                .id("built-id")
                .locale(Locale.FRANCE)
                .label("Catégorie")
                .turSNSiteFieldExt(fieldExt)
                .build();

        assertThat(built.getId()).isEqualTo("built-id");
        assertThat(built.getLocale()).isEqualTo(Locale.FRANCE);
        assertThat(built.getLabel()).isEqualTo("Catégorie");
        assertThat(built.getTurSNSiteFieldExt()).isEqualTo(fieldExt);
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        assertThat(facet.getId()).isNull();
        assertThat(facet.getLocale()).isNull();
        assertThat(facet.getLabel()).isNull();
        assertThat(facet.getTurSNSiteFieldExt()).isNull();
    }
}
