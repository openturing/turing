package com.viglet.turing.sn.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldRepository;

/**
 * Tests for TurSNSiteFieldService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldServiceTest {

    @Mock
    private TurSNSiteFieldRepository turSNSiteFieldRepository;

    private TurSNSiteFieldService service;

    @BeforeEach
    void setUp() {
        service = new TurSNSiteFieldService(turSNSiteFieldRepository);
    }

    @Test
    void toMapShouldBuildNameMap() {
        TurSNSite site = new TurSNSite();
        TurSNSiteField field1 = new TurSNSiteField();
        field1.setName("title");
        TurSNSiteField field2 = new TurSNSiteField();
        field2.setName("text");
        when(turSNSiteFieldRepository.findByTurSNSite(site)).thenReturn(List.of(field1, field2));

        Map<String, TurSNSiteField> map = service.toMap(site);

        assertThat(map)
                .containsEntry("title", field1)
                .containsEntry("text", field2)
                .hasSize(2);
    }

    @Test
    void toMapShouldReturnEmptyMapWhenNoFields() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteFieldRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        Map<String, TurSNSiteField> map = service.toMap(site);

        assertThat(map).isEmpty();
    }

    @Test
    void toMapShouldHandleSingleField() {
        TurSNSite site = new TurSNSite();
        TurSNSiteField field = new TurSNSiteField();
        field.setName("url");
        when(turSNSiteFieldRepository.findByTurSNSite(site)).thenReturn(List.of(field));

        Map<String, TurSNSiteField> map = service.toMap(site);

        assertThat(map).hasSize(1).containsEntry("url", field);
    }

    @Test
    void toMapShouldOverwriteDuplicateNames() {
        TurSNSite site = new TurSNSite();
        TurSNSiteField field1 = new TurSNSiteField();
        field1.setName("title");
        field1.setId("id-1");
        TurSNSiteField field2 = new TurSNSiteField();
        field2.setName("title");
        field2.setId("id-2");
        when(turSNSiteFieldRepository.findByTurSNSite(site)).thenReturn(List.of(field1, field2));

        Map<String, TurSNSiteField> map = service.toMap(site);

        // Last one wins in a simple HashMap put
        assertThat(map).hasSize(1).containsKey("title");
        assertThat(map.get("title")).isSameAs(field2);
    }

    @Test
    void toMapShouldPreserveAllDistinctFieldNames() {
        TurSNSite site = new TurSNSite();
        TurSNSiteField f1 = new TurSNSiteField();
        f1.setName("title");
        TurSNSiteField f2 = new TurSNSiteField();
        f2.setName("description");
        TurSNSiteField f3 = new TurSNSiteField();
        f3.setName("author");
        TurSNSiteField f4 = new TurSNSiteField();
        f4.setName("date");
        TurSNSiteField f5 = new TurSNSiteField();
        f5.setName("category");
        when(turSNSiteFieldRepository.findByTurSNSite(site))
                .thenReturn(List.of(f1, f2, f3, f4, f5));

        Map<String, TurSNSiteField> map = service.toMap(site);

        assertThat(map).hasSize(5);
        assertThat(map.keySet()).containsExactlyInAnyOrder(
                "title", "description", "author", "date", "category");
    }

    @Test
    void toMapShouldReturnMutableMap() {
        TurSNSite site = new TurSNSite();
        TurSNSiteField field = new TurSNSiteField();
        field.setName("title");
        when(turSNSiteFieldRepository.findByTurSNSite(site)).thenReturn(List.of(field));

        Map<String, TurSNSiteField> map = service.toMap(site);

        // Verify map is mutable (HashMap)
        TurSNSiteField newField = new TurSNSiteField();
        newField.setName("extra");
        map.put("extra", newField);

        assertThat(map).hasSize(2);
    }
}
