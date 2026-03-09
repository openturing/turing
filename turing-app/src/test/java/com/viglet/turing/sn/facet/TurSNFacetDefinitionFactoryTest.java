package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

class TurSNFacetDefinitionFactoryTest {

        private final TurSNFacetDefinitionFactory factory = new TurSNFacetDefinitionFactory();

        @Test
        void shouldReturnEmptyWhenFieldsListIsNull() {
                List<TurSNFacetDefinition> result = factory.fromFields(null, Locale.US);

                assertThat(result).isEmpty();
        }

        @Test
        void shouldCreateFieldAndCustomFacetDefinitionsFromField() {
                TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                                .id("custom-1")
                                .name("price_range")
                                .build();
                TurSNSiteFieldExtFacet localeFacet = TurSNSiteFieldExtFacet.builder().label("Price").build();
                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("price")
                                .facet(1)
                                .customFacets(Set.of(customFacet))
                                .build();

                List<TurSNFacetDefinition> definitions = factory.fromField(fieldExt, Locale.US, Set.of(localeFacet));

                assertThat(definitions)
                                .hasSize(2)
                                .anyMatch(TurSNFieldFacetDefinition.class::isInstance)
                                .anyMatch(TurSNCustomFacetDefinition.class::isInstance);
        }

        @Test
        void shouldUseProvidedFacetLocaleProvider() {
                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("price")
                                .facet(1)
                                .build();
                TurSNSiteFieldExtFacet providedFacet = TurSNSiteFieldExtFacet.builder().label("Provided").build();

                List<TurSNFacetDefinition> definitions = factory.fromFields(
                                List.of(fieldExt),
                                Locale.US,
                                ignored -> Set.of(providedFacet));

                TurSNFacetDefinition fieldDefinition = definitions.stream()
                                .filter(TurSNFieldFacetDefinition.class::isInstance)
                                .findFirst()
                                .orElseThrow();

                assertThat(fieldDefinition.getFacetLocales()).containsExactly(providedFacet);
        }

        @Test
        void shouldUseFieldFacetLocalesByDefault() {
                TurSNSiteFieldExtFacet fieldLocaleFacet = TurSNSiteFieldExtFacet.builder().label("Field Locale")
                                .build();
                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("price")
                                .facet(1)
                                .facetLocales(Set.of(fieldLocaleFacet))
                                .build();

                List<TurSNFacetDefinition> definitions = factory.fromFields(List.of(fieldExt), Locale.US);

                assertThat(definitions)
                                .filteredOn(TurSNFieldFacetDefinition.class::isInstance)
                                .singleElement()
                                .satisfies(def -> assertThat(def.getFacetLocales()).containsExactly(fieldLocaleFacet));
        }
}
