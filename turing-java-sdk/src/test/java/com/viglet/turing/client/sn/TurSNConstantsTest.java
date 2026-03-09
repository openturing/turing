package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class TurSNConstantsTest {

    @Test
    void shouldExposeExpectedFieldConstants() {
        assertThat(TurSNConstants.ID_ATTR).isEqualTo("id");
        assertThat(TurSNConstants.TITLE_ATTR).isEqualTo("title");
        assertThat(TurSNConstants.ABSTRACT_ATTR).isEqualTo("abstract");
        assertThat(TurSNConstants.URL_ATTR).isEqualTo("url");
        assertThat(TurSNConstants.TEXT_ATTR).isEqualTo("text");
        assertThat(TurSNConstants.SOURCE_APPS_ATTR).isEqualTo("source_apps");
        assertThat(TurSNConstants.TYPE_ATTR).isEqualTo("type");
        assertThat(TurSNConstants.SITE_ATTR).isEqualTo("site");
    }

    @Test
    void utilityConstructorShouldThrowIllegalStateException() throws Exception {
        Constructor<TurSNConstants> constructor = TurSNConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(ex.getCause().getMessage()).isEqualTo("SN Constants class");
        }
    }
}
