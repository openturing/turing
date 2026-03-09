package com.viglet.turing.persistence.model.sn.field;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;

@ExtendWith(MockitoExtension.class)
class TurSNSiteFieldTest {

    @Mock
    private TurSNSite turSNSite;

    private TurSNSiteField turSNSiteField;

    @BeforeEach
    void setUp() {
        turSNSiteField = new TurSNSiteField();
    }

    @Test
    void shouldSetAndGetAllProperties() {
        turSNSiteField.setId("field-id");
        turSNSiteField.setName("title");
        turSNSiteField.setDescription("Title field");
        turSNSiteField.setType(TurSEFieldType.STRING);
        turSNSiteField.setMultiValued(1);
        turSNSiteField.setTurSNSite(turSNSite);

        assertThat(turSNSiteField.getId()).isEqualTo("field-id");
        assertThat(turSNSiteField.getName()).isEqualTo("title");
        assertThat(turSNSiteField.getDescription()).isEqualTo("Title field");
        assertThat(turSNSiteField.getType()).isEqualTo(TurSEFieldType.STRING);
        assertThat(turSNSiteField.getMultiValued()).isEqualTo(1);
        assertThat(turSNSiteField.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void shouldCreateUsingBuilder() {
        TurSNSiteField field = TurSNSiteField.builder()
                .id("builder-id")
                .name("content")
                .description("Body content")
                .type(TurSEFieldType.TEXT)
                .multiValued(0)
                .turSNSite(turSNSite)
                .build();

        assertThat(field.getId()).isEqualTo("builder-id");
        assertThat(field.getName()).isEqualTo("content");
        assertThat(field.getDescription()).isEqualTo("Body content");
        assertThat(field.getType()).isEqualTo(TurSEFieldType.TEXT);
        assertThat(field.getMultiValued()).isZero();
        assertThat(field.getTurSNSite()).isEqualTo(turSNSite);
    }

    @Test
    void shouldExposeDefaultValuesOnNoArgsConstructor() {
        assertThat(turSNSiteField.getId()).isNull();
        assertThat(turSNSiteField.getName()).isNull();
        assertThat(turSNSiteField.getDescription()).isNull();
        assertThat(turSNSiteField.getType()).isNull();
        assertThat(turSNSiteField.getMultiValued()).isZero();
        assertThat(turSNSiteField.getTurSNSite()).isNull();
    }
}
