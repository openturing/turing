package com.viglet.core.lang;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UrlTemplate}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class UrlTemplateTest {

    @Test
    void constructorShouldThrowIllegalStateException() throws NoSuchMethodException {
        var constructor = UrlTemplate.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Utility class");
    }

    @Test
    void ofShouldConcatenateWithSlash() {
        assertThat(UrlTemplate.of("http://localhost", "123"))
                .isEqualTo("http://localhost/123");
    }

    @Test
    void ofShouldHandleEmptyId() {
        assertThat(UrlTemplate.of("http://localhost", ""))
                .isEqualTo("http://localhost/");
    }

    @Test
    void ofShouldHandleTrailingSlashInServiceUrl() {
        assertThat(UrlTemplate.of("http://localhost/", "abc"))
                .isEqualTo("http://localhost//abc");
    }

    @Test
    void ofShouldHandleEmptyServiceUrl() {
        assertThat(UrlTemplate.of("", "abc"))
                .isEqualTo("/abc");
    }

    @Test
    void ofShouldHandleBothEmpty() {
        assertThat(UrlTemplate.of("", ""))
                .isEqualTo("/");
    }

    @Test
    void ofShouldThrowOnNullServiceUrl() {
        assertThatThrownBy(() -> UrlTemplate.of(null, "id"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ofShouldThrowOnNullId() {
        assertThatThrownBy(() -> UrlTemplate.of("http://localhost", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ofShouldHandleSpecialCharactersInId() {
        assertThat(UrlTemplate.of("http://localhost", "my id with spaces"))
                .isEqualTo("http://localhost/my id with spaces");
    }

    @Test
    void ofShouldHandleUrlWithPort() {
        assertThat(UrlTemplate.of("http://localhost:8080/api", "resource"))
                .isEqualTo("http://localhost:8080/api/resource");
    }

    @Test
    void ofShouldHandleUuidId() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        assertThat(UrlTemplate.of("http://localhost/api", uuid))
                .isEqualTo("http://localhost/api/" + uuid);
    }
}
