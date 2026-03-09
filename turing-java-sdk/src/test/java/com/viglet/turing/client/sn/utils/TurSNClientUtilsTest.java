package com.viglet.turing.client.sn.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.auth.credentials.TurUsernamePasswordCredentials;

class TurSNClientUtilsTest {

    @Test
    void utilityConstructorShouldThrowIllegalStateException() throws Exception {
        Constructor<TurSNClientUtils> constructor = TurSNClientUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldSetApiKeyHeaderWhenApiKeyIsProvided() {
        HttpPost httpPost = new HttpPost("http://localhost");

        TurSNClientUtils.authentication(httpPost, new TurUsernamePasswordCredentials("u", "p"), "api-key");

        assertThat(httpPost.getFirstHeader("Key").getValue()).isEqualTo("api-key");
        assertThat(httpPost.getFirstHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void shouldSetBasicAuthorizationWhenCredentialsAreProvidedWithoutApiKey() {
        HttpPost httpPost = new HttpPost("http://localhost");
        TurUsernamePasswordCredentials credentials = new TurUsernamePasswordCredentials("john", "secret");

        TurSNClientUtils.authentication(httpPost, credentials, null);

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("john:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(httpPost.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue()).isEqualTo(expected);
    }
}
