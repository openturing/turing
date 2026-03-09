package com.viglet.turing.persistence.dto.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

class TurSNSiteFieldExtFacetDtoTest {

    @Test
    void shouldMapFromEntityConstructor() {
        TurSNSiteFieldExtFacet entity = new TurSNSiteFieldExtFacet();
        entity.setId("facet-id");
        entity.setLocale(Locale.US);
        entity.setLabel("Label EN");

        TurSNSiteFieldExtFacetDto dto = new TurSNSiteFieldExtFacetDto(entity);

        assertThat(dto.getId()).isEqualTo("facet-id");
        assertThat(dto.getLocale()).isEqualTo(Locale.US);
        assertThat(dto.getLabel()).isEqualTo("Label EN");
    }

    @Test
    void shouldCreateUsingBuilderAndSetters() {
        TurSNSiteFieldExtFacetDto dto = TurSNSiteFieldExtFacetDto.builder()
                .id("built-id")
                .locale(Locale.JAPAN)
                .label("ラベル")
                .build();

        assertThat(dto.getId()).isEqualTo("built-id");
        assertThat(dto.getLocale()).isEqualTo(Locale.JAPAN);
        assertThat(dto.getLabel()).isEqualTo("ラベル");

        dto.setLabel("更新");
        assertThat(dto.getLabel()).isEqualTo("更新");
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        TurSNSiteFieldExtFacetDto dto = new TurSNSiteFieldExtFacetDto();

        assertThat(dto.getId()).isNull();
        assertThat(dto.getLocale()).isNull();
        assertThat(dto.getLabel()).isNull();
    }
}
