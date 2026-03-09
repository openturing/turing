package com.viglet.turing.commons.sn.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class TurSNFieldNameTest {

    @Test
    void shouldExposeSemanticNavigationFieldConstants() {
        assertThat(TurSNFieldName.ID).isEqualTo("id");
        assertThat(TurSNFieldName.URL).isEqualTo("url");
        assertThat(TurSNFieldName.ABSTRACT).isEqualTo("abstract");
        assertThat(TurSNFieldName.TEXT).isEqualTo("text");
        assertThat(TurSNFieldName.TITLE).isEqualTo("title");
        assertThat(TurSNFieldName.SOURCE_APPS).isEqualTo("source_apps");
        assertThat(TurSNFieldName.DEFAULT).isEqualTo("_text_");
        assertThat(TurSNFieldName.EXACT_MATCH).isEqualTo("exact_match");
    }

    @Test
    void utilityConstructorShouldThrow() throws Exception {
        Constructor<TurSNFieldName> constructor = TurSNFieldName.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }
}
