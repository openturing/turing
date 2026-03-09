package com.viglet.turing.sn.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;

class TurSNFieldDefinitionTest {

    @Test
    void shouldExposeRecordComponents() {
        TurSNSiteFieldExtFacet localeFacet = new TurSNSiteFieldExtFacet();
        localeFacet.setLocale(Locale.US);

        TurSNFieldDefinition definition = new TurSNFieldDefinition(
                "title",
                "Title field",
                TurSEFieldType.TEXT,
                0,
                "category",
                Set.of(localeFacet),
                1);

        assertEquals("title", definition.name());
        assertEquals("Title field", definition.description());
        assertEquals(TurSEFieldType.TEXT, definition.type());
        assertEquals(0, definition.multiValued());
        assertEquals("category", definition.facetName());
        assertEquals(1, definition.hl());
        assertEquals(1, definition.locales().size());
        assertEquals(Locale.US, definition.locales().iterator().next().getLocale());
    }
}
