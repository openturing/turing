package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.TurSNFieldType;

@ExtendWith(MockitoExtension.class)
class TurSNSiteCustomFacetAPITest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;

    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    private TurSNSiteCustomFacetAPI api;

    @BeforeEach
    void setUp() {
        api = new TurSNSiteCustomFacetAPI(turSNSiteRepository, turSNSiteFieldExtRepository);
    }

    @Test
    void shouldListCustomFacetsSortedByPositionAndName() {
        TurSNSite site = site("site-1");
        TurSNSiteCustomFacet facetA = customFacet("f-a", "z-name", 2);
        TurSNSiteCustomFacet facetB = customFacet("f-b", "a-name", 1);
        TurSNSiteFieldExt fieldA = fieldExt("field-a", "price", TurSEFieldType.INT, site, Set.of(facetA));
        TurSNSiteFieldExt fieldB = fieldExt("field-b", "category", TurSEFieldType.STRING, site, Set.of(facetB));

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(fieldA, fieldB));

        List<TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto> result = api.list("site-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("f-b");
        assertThat(result.get(1).getId()).isEqualTo("f-a");
        assertThat(result.get(0).getFieldExtId()).isEqualTo("field-b");
    }

    @Test
    void shouldGetCustomFacetWithMappedItemsAndFieldMetadata() {
        TurSNSite site = site("site-1");
        TurSNSiteCustomFacetItem itemOne = TurSNSiteCustomFacetItem.builder()
                .id("item-1")
                .label("1-10")
                .position(2)
                .build();
        TurSNSiteCustomFacetItem itemTwo = TurSNSiteCustomFacetItem.builder()
                .id("item-2")
                .label("0-1")
                .position(1)
                .rangeStartDate(Instant.parse("2026-03-01T00:00:00Z"))
                .rangeEndDate(Instant.parse("2026-03-31T00:00:00Z"))
                .build();
        TurSNSiteCustomFacet facet = customFacet("facet-1", "price_range", 1);
        facet.setLabel(Map.of("en-US", "Price Range"));
        facet.setItems(Set.of(itemOne, itemTwo));
        TurSNSiteFieldExt field = fieldExt("field-1", "price", TurSEFieldType.DATE, site, Set.of(facet));

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(field));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto dto = api.get("site-1", "facet-1");

        assertThat(dto.getId()).isEqualTo("facet-1");
        assertThat(dto.getFieldExtId()).isEqualTo("field-1");
        assertThat(dto.getFieldExtName()).isEqualTo("price");
        assertThat(dto.getFieldExtType()).isEqualTo("DATE");
        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getItems().get(0).getId()).isEqualTo("item-2");
        assertThat(dto.getItems().get(0).getRangeStartDate()).isEqualTo("2026-03-01T00:00:00Z");
    }

    @Test
    void shouldRejectCreateWhenFieldExtIdIsMissing() {
        TurSNSite site = site("site-1");
        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto payload = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto();
        payload.setName("price_range");

        assertThatThrownBy(() -> api.create("site-1", payload))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void shouldCreateCustomFacetWithAutoPositionAndDateParsing() {
        TurSNSite site = site("site-1");
        TurSNSiteCustomFacet existing = customFacet("existing", "existing_range", 3);
        TurSNSiteFieldExt field = fieldExt("field-1", "publishDate", TurSEFieldType.DATE, site, Set.of(existing));

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(field));
        when(turSNSiteFieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetItemDto item = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetItemDto();
        item.setId("new-item");
        item.setLabel("March");
        item.setPosition(1);
        item.setRangeStartDate("2026-03-01");
        item.setRangeEndDate("2026-03-31T23:59:59Z");

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto payload = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto();
        payload.setFieldExtId("field-1");
        payload.setName("month_range");
        payload.setDefaultLabel("Month Range");
        payload.setItems(List.of(item));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto result = api.create("site-1", payload);

        assertThat(result.getName()).isEqualTo("month_range");
        assertThat(result.getFacetPosition()).isEqualTo(4);
        assertThat(result.getFacetType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(result.getFacetItemType()).isEqualTo(TurSNSiteFacetFieldEnum.DEFAULT);
        assertThat(result.getItems()).singleElement().satisfies(savedItem -> {
            assertThat(savedItem.getRangeStartDate()).isEqualTo("2026-03-01T00:00:00Z");
            assertThat(savedItem.getRangeStart()).isNull();
        });
    }

    @Test
    void shouldUpdateFacetAndMoveToAnotherField() {
        TurSNSite site = site("site-1");
        TurSNSiteCustomFacet facet = customFacet("facet-1", "old_name", 2);
        TurSNSiteFieldExt sourceField = fieldExt("field-source", "price", TurSEFieldType.INT, site, Set.of(facet));
        TurSNSiteFieldExt targetField = fieldExt("field-target", "score", TurSEFieldType.INT, site, Set.of());

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(sourceField, targetField));
        when(turSNSiteFieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetItemDto item = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetItemDto();
        item.setLabel("100-200");
        item.setPosition(1);
        item.setRangeStart(new BigDecimal("100"));
        item.setRangeEnd(new BigDecimal("200"));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto payload = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto();
        payload.setFieldExtId("field-target");
        payload.setName("new_name");
        payload.setFacetPosition(7);
        payload.setItems(List.of(item));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto result = api.update("site-1", "facet-1", payload);

        assertThat(result.getId()).isEqualTo("facet-1");
        assertThat(result.getName()).isEqualTo("new_name");
        assertThat(result.getFieldExtId()).isEqualTo("field-target");
        assertThat(result.getFacetPosition()).isEqualTo(7);
        assertThat(result.getItems()).singleElement().satisfies(savedItem -> {
            assertThat(savedItem.getRangeStart()).isEqualByComparingTo("100");
            assertThat(savedItem.getRangeStartDate()).isNull();
        });

        ArgumentCaptor<TurSNSiteFieldExt> captor = ArgumentCaptor.forClass(TurSNSiteFieldExt.class);
        verify(turSNSiteFieldExtRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("field-target");
    }

    @Test
    void shouldRejectUpdateWhenFacetDoesNotExist() {
        TurSNSite site = site("site-1");
        TurSNSiteFieldExt field = fieldExt("field-1", "price", TurSEFieldType.INT, site, Set.of());

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(field));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto payload = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto();
        payload.setName("new_name");

        assertThatThrownBy(() -> api.update("site-1", "missing", payload))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(NOT_FOUND);
    }

    @Test
    void shouldRejectUpdateWhenDateIsInvalid() {
        TurSNSite site = site("site-1");
        TurSNSiteCustomFacet facet = customFacet("facet-1", "date_range", 1);
        TurSNSiteFieldExt sourceField = fieldExt("field-source", "publishDate", TurSEFieldType.DATE, site,
                Set.of(facet));

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(sourceField));

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetItemDto item = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetItemDto();
        item.setLabel("Invalid");
        item.setRangeStartDate("not-a-date");

        TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto payload = new TurSNSiteCustomFacetAPI.TurSNSiteCustomFacetDto();
        payload.setName("date_range");
        payload.setItems(List.of(item));

        assertThatThrownBy(() -> api.update("site-1", "facet-1", payload))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void shouldDeleteFacetFromFieldAndPersistChanges() {
        TurSNSite site = site("site-1");
        TurSNSiteCustomFacet removable = customFacet("facet-1", "removable", 1);
        TurSNSiteCustomFacet keep = customFacet("facet-2", "keep", 2);
        TurSNSiteFieldExt field = fieldExt("field-1", "price", TurSEFieldType.INT, site, Set.of(removable, keep));

        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSite(any(Sort.class), eq(site)))
                .thenReturn(List.of(field));
        when(turSNSiteFieldExtRepository.save(any(TurSNSiteFieldExt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean deleted = api.delete("site-1", "facet-1");

        assertThat(deleted).isTrue();

        ArgumentCaptor<TurSNSiteFieldExt> captor = ArgumentCaptor.forClass(TurSNSiteFieldExt.class);
        verify(turSNSiteFieldExtRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomFacets())
                .extracting(TurSNSiteCustomFacet::getId)
                .containsExactly("facet-2");
    }

    private TurSNSite site(String id) {
        TurSNSite site = new TurSNSite();
        site.setId(id);
        return site;
    }

    private TurSNSiteFieldExt fieldExt(String id,
            String name,
            TurSEFieldType type,
            TurSNSite site,
            Set<TurSNSiteCustomFacet> customFacets) {
        TurSNSiteFieldExt fieldExt = TurSNSiteFieldExt.builder()
                .id(id)
                .externalId(id + "-ext")
                .name(name)
                .type(type)
                .snType(TurSNFieldType.SE)
                .turSNSite(site)
                .build();
        fieldExt.setCustomFacets(customFacets);
        return fieldExt;
    }

    private TurSNSiteCustomFacet customFacet(String id, String name, Integer position) {
        return TurSNSiteCustomFacet.builder()
                .id(id)
                .name(name)
                .facetPosition(position)
                .build();
    }
}
