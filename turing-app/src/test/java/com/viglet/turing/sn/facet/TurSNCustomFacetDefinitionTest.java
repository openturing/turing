package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;

class TurSNCustomFacetDefinitionTest {

    @Test
    void shouldPrioritizeDefaultLabel() {
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("custom-1")
                .name("price_range")
                .defaultLabel("Price Range")
                .label(Map.of("pt-BR", "Faixa de Preco"))
                .build();

        TurSNCustomFacetDefinition definition = new TurSNCustomFacetDefinition(
                TurSNSiteFieldExt.builder().id("field-1").build(),
                customFacet,
                Locale.US);

        assertThat(definition.getLabel()).isEqualTo("Price Range");
    }

    @Test
    void shouldUseSortedFirstLabelWhenDefaultMissing() {
        Map<String, String> labels = new HashMap<>();
        labels.put("pt-BR", "Faixa de Preco");
        labels.put("en-US", "Price Range");

        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .name("price_range")
                .label(labels)
                .build();

        TurSNCustomFacetDefinition definition = new TurSNCustomFacetDefinition(
                TurSNSiteFieldExt.builder().id("field-1").build(),
                customFacet,
                Locale.US);

        assertThat(definition.getLabel()).isEqualTo("Price Range");
    }

    @Test
    void shouldFallbackToNameWhenLabelsAreBlank() {
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .name("price_range")
                .defaultLabel(" ")
                .label(Map.of("en-US", " "))
                .build();

        TurSNCustomFacetDefinition definition = new TurSNCustomFacetDefinition(
                TurSNSiteFieldExt.builder().id("field-1").build(),
                customFacet,
                Locale.US);

        assertThat(definition.getLabel()).isEqualTo("price_range");
    }

    @Test
    void shouldMapFacetLocalesFromLabelMapAndIgnoreBlank() {
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .name("price_range")
                .label(Map.of(
                        "en-US", "Price Range",
                        "pt-BR", " "))
                .build();

        TurSNCustomFacetDefinition definition = new TurSNCustomFacetDefinition(
                TurSNSiteFieldExt.builder().id("field-1").build(),
                customFacet,
                Locale.US);

        assertThat(definition.getFacetLocales())
                .hasSize(1)
                .allSatisfy(localeFacet -> {
                    assertThat(localeFacet.getLocale()).isEqualTo(Locale.forLanguageTag("en-US"));
                    assertThat(localeFacet.getLabel()).isEqualTo("Price Range");
                });
    }

    @Test
    void shouldFallbackFacetLocalesToCurrentLocaleWhenNoLabelMap() {
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .name("price_range")
                .defaultLabel("Price Range")
                .label(null)
                .build();

        TurSNCustomFacetDefinition definition = new TurSNCustomFacetDefinition(
                TurSNSiteFieldExt.builder().id("field-1").build(),
                customFacet,
                Locale.CANADA);

        assertThat(definition.getFacetLocales())
                .hasSize(1)
                .allSatisfy(localeFacet -> {
                    assertThat(localeFacet.getLocale()).isEqualTo(Locale.CANADA);
                    assertThat(localeFacet.getLabel()).isEqualTo("Price Range");
                });
    }

    @Test
    void shouldExposeFacetTypeItemsAndCustomFlag() {
        TurSNSiteCustomFacetItem item = TurSNSiteCustomFacetItem.builder().id("item-1").build();
        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("custom-1")
                .name("price_range")
                .facetPosition(8)
                .facetType(TurSNSiteFacetFieldEnum.AND)
                .facetItemType(TurSNSiteFacetFieldEnum.OR)
                .items(Set.of(item))
                .build();

        TurSNCustomFacetDefinition definition = new TurSNCustomFacetDefinition(
                TurSNSiteFieldExt.builder().id("field-1").build(),
                customFacet,
                Locale.US);

        assertThat(definition.getId()).isEqualTo("custom-1");
        assertThat(definition.getName()).isEqualTo("price_range");
        assertThat(definition.getPosition()).isEqualTo(8);
        assertThat(definition.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.AND);
        assertThat(definition.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.OR);
        assertThat(definition.isCustomFacet()).isTrue();
        assertThat(definition.getItems()).containsExactly(item);
    }
}
