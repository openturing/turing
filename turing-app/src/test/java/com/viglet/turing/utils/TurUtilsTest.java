package com.viglet.turing.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

/**
 * Tests for TurUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurUtilsTest {

    @Test
    void constructorShouldThrowIllegalStateException() throws NoSuchMethodException {
        var constructor = TurUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Utility class");
    }

    @Test
    void getUrlTemplateShouldConcatenateWithSlash() {
        assertThat(TurUtils.getUrlTemplate("http://localhost", "123"))
                .isEqualTo("http://localhost/123");
    }

    @Test
    void getUrlTemplateShouldHandleEmptyId() {
        assertThat(TurUtils.getUrlTemplate("http://localhost", ""))
                .isEqualTo("http://localhost/");
    }

    @Test
    void getUrlTemplateShouldHandleTrailingSlashInServiceUrl() {
        assertThat(TurUtils.getUrlTemplate("http://localhost/", "abc"))
                .isEqualTo("http://localhost//abc");
    }

    @Test
    void getUrlTemplateShouldHandleEmptyServiceUrl() {
        assertThat(TurUtils.getUrlTemplate("", "abc"))
                .isEqualTo("/abc");
    }

    @Test
    void getUrlTemplateShouldHandleBothEmpty() {
        assertThat(TurUtils.getUrlTemplate("", ""))
                .isEqualTo("/");
    }

    @Test
    void getUrlTemplateShouldThrowOnNullServiceUrl() {
        assertThatThrownBy(() -> TurUtils.getUrlTemplate(null, "id"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getUrlTemplateShouldThrowOnNullId() {
        assertThatThrownBy(() -> TurUtils.getUrlTemplate("http://localhost", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getUrlTemplateShouldHandleSpecialCharactersInId() {
        assertThat(TurUtils.getUrlTemplate("http://localhost", "my id with spaces"))
                .isEqualTo("http://localhost/my id with spaces");
    }

    @Test
    void getUrlTemplateShouldHandleUrlWithPort() {
        assertThat(TurUtils.getUrlTemplate("http://localhost:8080/api", "resource"))
                .isEqualTo("http://localhost:8080/api/resource");
    }

    @Test
    void getUrlTemplateShouldHandleUuidId() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertThat(TurUtils.getUrlTemplate("http://localhost/api", uuid))
                .isEqualTo("http://localhost/api/" + uuid);
    }
}
