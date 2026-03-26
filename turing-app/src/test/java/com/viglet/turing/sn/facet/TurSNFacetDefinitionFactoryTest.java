package com.viglet.turing.sn.facet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

/**
 * Tests for TurSNFacetDefinitionFactory.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSNFacetDefinitionFactoryTest {

    @Mock
    private TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository;

    private TurSNFacetDefinitionFactory factory() {
        return new TurSNFacetDefinitionFactory(turSNSiteCustomFacetRepository);
    }

    // --- fromFields(List, Locale) ---

    @Test
    void shouldReturnEmptyWhenFieldsListIsNull() {
        List<TurSNFacetDefinition> result = factory().fromFields(null, Locale.US);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenFieldsListIsEmpty() {
        List<TurSNFacetDefinition> result = factory().fromFields(Collections.emptyList(), Locale.US);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipFieldsWithFacetZero() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());

        TurSNSiteFieldExt nonFacetField = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("description")
                .facet(0)
                .build();

        List<TurSNFacetDefinition> result = factory().fromFields(List.of(nonFacetField), Locale.US);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldCreateFieldFacetDefinitionForFacetField() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());

        TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("category")
                .facet(1)
                .build();

        List<TurSNFacetDefinition> result = factory().fromFields(List.of(facetField), Locale.US);

        assertThat(result)
                .hasSize(1)
                .allMatch(TurSNFieldFacetDefinition.class::isInstance);
    }

    @Test
    void shouldCreateBothFieldAndCustomFacetDefinitions() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("price")
                .facet(1)
                .build();

        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("custom-1")
                .name("price_range")
                .build();
        customFacet.setTurSNSiteFieldExt(fieldExt);

        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet));

        List<TurSNFacetDefinition> result = factory().fromFields(List.of(fieldExt), Locale.US);

        assertThat(result)
                .hasSize(2)
                .anyMatch(TurSNFieldFacetDefinition.class::isInstance)
                .anyMatch(TurSNCustomFacetDefinition.class::isInstance);
    }

    @Test
    void shouldCreateOnlyCustomFacetWhenFieldFacetIsZero() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("price")
                .facet(0)
                .build();

        TurSNSiteCustomFacet customFacet = TurSNSiteCustomFacet.builder()
                .id("custom-1")
                .name("price_range")
                .build();
        customFacet.setTurSNSiteFieldExt(fieldExt);

        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(List.of(customFacet));

        List<TurSNFacetDefinition> result = factory().fromFields(List.of(fieldExt), Locale.US);

        assertThat(result)
                .hasSize(1)
                .allMatch(TurSNCustomFacetDefinition.class::isInstance);
    }

    @Test
    void shouldProcessMultipleFieldsWithMixedFacetSettings() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());

        TurSNSiteFieldExt facetField = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("category")
                .facet(1)
                .build();
        TurSNSiteFieldExt nonFacetField = TurSNSiteFieldExt.builder()
                .id("field-2")
                .name("description")
                .facet(0)
                .build();

        List<TurSNFacetDefinition> result = factory().fromFields(
                List.of(facetField, nonFacetField), Locale.US);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("category");
    }

    // --- fromFields with custom provider ---

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
                List.of(fieldExt), Locale.US, ignored -> Set.of(providedFacet));

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
        TurSNSiteFieldExtFacet fieldLocaleFacet = TurSNSiteFieldExtFacet.builder().label("Field Locale").build();
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

    @Test
    void shouldHandleNullFacetLocalesOnFieldExt() {
        when(turSNSiteCustomFacetRepository.findByFieldExtsWithDetails(any()))
                .thenReturn(Collections.emptyList());
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("price")
                .facet(1)
                .build();
        // facetLocales defaults to empty set via @Builder.Default

        List<TurSNFacetDefinition> definitions = factory().fromFields(List.of(fieldExt), Locale.US);

        assertThat(definitions).hasSize(1);
        assertThat(definitions.get(0).getFacetLocales()).isEmpty();
    }

    // --- fromField(fieldExt, locale, facetLocales) ---

    @Test
    void fromFieldShouldQueryRepositoryForCustomFacets() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("category")
                .facet(1)
                .build();
        when(turSNSiteCustomFacetRepository.findByFieldExtWithDetails(fieldExt))
                .thenReturn(Collections.emptyList());

        List<TurSNFacetDefinition> result = factory().fromField(fieldExt, Locale.US, Collections.emptySet());

        verify(turSNSiteCustomFacetRepository).findByFieldExtWithDetails(fieldExt);
        assertThat(result).hasSize(1);
    }

    // --- fromField(fieldExt, locale, facetLocales, customFacets) ---

    @Test
    void fromFieldWithCustomFacetsShouldCreateBothTypes() {
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
    void fromFieldWithNullCustomFacetsShouldOnlyCreateFieldFacet() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("category")
                .facet(1)
                .build();

        List<TurSNFacetDefinition> result = factory().fromField(fieldExt, Locale.US,
                Collections.emptySet(), null);

        assertThat(result)
                .hasSize(1)
                .allMatch(TurSNFieldFacetDefinition.class::isInstance);
    }

    @Test
    void fromFieldWithMultipleCustomFacetsShouldCreateAll() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("price")
                .facet(0)
                .build();

        TurSNSiteCustomFacet cf1 = TurSNSiteCustomFacet.builder().id("cf-1").name("range1").build();
        TurSNSiteCustomFacet cf2 = TurSNSiteCustomFacet.builder().id("cf-2").name("range2").build();
        TurSNSiteCustomFacet cf3 = TurSNSiteCustomFacet.builder().id("cf-3").name("range3").build();

        List<TurSNFacetDefinition> result = factory().fromField(fieldExt, Locale.US,
                Collections.emptySet(), List.of(cf1, cf2, cf3));

        assertThat(result)
                .hasSize(3)
                .allMatch(TurSNCustomFacetDefinition.class::isInstance);
    }

    @Test
    void fromFieldShouldReturnEmptyWhenNoFacetAndNoCustom() {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id("field-1")
                .name("title")
                .facet(0)
                .build();

        List<TurSNFacetDefinition> result = factory().fromField(fieldExt, Locale.US,
                Collections.emptySet(), Collections.emptyList());

        assertThat(result).isEmpty();
    }
}
