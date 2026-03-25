package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;

@ExtendWith(MockitoExtension.class)
class TurSNFacetDefinitionFactoryTest {

        @Mock
        private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

        private TurSNFacetDefinitionFactory factory() {
                return new TurSNFacetDefinitionFactory(turSNSiteCustomFacetRepository);
        }

        @Test
        void shouldReturnEmptyWhenFieldsListIsNull() {
                List<TurSNFacetDefinition> result = factory().fromFields(null, Locale.US);

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
                                .build();

                List<TurSNFacetDefinition> definitions = factory().fromField(fieldExt, Locale.US,
                                Set.of(localeFacet), List.of(customFacet));

                assertThat(definitions)
                                .hasSize(2)
                                .anyMatch(TurSNFieldFacetDefinition.class::isInstance)
                                .anyMatch(TurSNCustomFacetDefinition.class::isInstance);
        }

        @Test
        void shouldUseProvidedFacetLocaleProvider() {
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(Collections.emptyList());
                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("price")
                                .facet(1)
                                .build();
                TurSNSiteFieldExtFacet providedFacet = TurSNSiteFieldExtFacet.builder().label("Provided").build();

                List<TurSNFacetDefinition> definitions = factory().fromFields(
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
                when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                                .thenReturn(Collections.emptyList());
                TurSNSiteFieldExtFacet fieldLocaleFacet = TurSNSiteFieldExtFacet.builder().label("Field Locale")
                                .build();
                TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                                .id("field-1")
                                .name("price")
                                .facet(1)
                                .facetLocales(Set.of(fieldLocaleFacet))
                                .build();

                List<TurSNFacetDefinition> definitions = factory().fromFields(List.of(fieldExt), Locale.US);

                assertThat(definitions)
                                .filteredOn(TurSNFieldFacetDefinition.class::isInstance)
                                .singleElement()
                                .satisfies(def -> assertThat(def.getFacetLocales()).containsExactly(fieldLocaleFacet));
        }
}
