package com.viglet.turing.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Optional;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class TurIntegrationAPITest {

    private static void invokeProxy(TurIntegrationAPI api, TurIntegrationInstance instance,
            MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        Method method = TurIntegrationAPI.class.getDeclaredMethod("proxy", TurIntegrationInstance.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
        method.setAccessible(true);
        method.invoke(api, instance, request, response);
    }

    @Mock
    private TurIntegrationInstanceRepository turIntegrationInstanceRepository;

    @Mock
    private CloseableHttpClient proxyHttpClient;

    @InjectMocks
    private TurIntegrationAPI turIntegrationAPI;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void testIndexAnyRequest_Found() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("1");
        instance.setEndpoint("http://example.com/api/v2/integration/1");

        when(turIntegrationInstanceRepository.findById("1")).thenReturn(Optional.of(instance));

        request.setRequestURI("/api/v2/integration/1");
        request.setMethod("GET");

        turIntegrationAPI.indexAnyRequest(request, response, "1");

        verify(turIntegrationInstanceRepository, times(1)).findById("1");
    }

    @Test
    void testIndexAnyRequest_NotFound() {
        when(turIntegrationInstanceRepository.findById("1")).thenReturn(Optional.empty());

        turIntegrationAPI.indexAnyRequest(request, response, "1");

        verify(turIntegrationInstanceRepository, times(1)).findById("1");
        verifyNoInteractions(proxyHttpClient);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v2/integration/1/../../secret, Forbidden proxy path",
        "/api/v1/integration/1/something, Forbidden proxy path",
        "http://malicious.com/api/v2/integration/1/something, Forbidden proxy target"
    })
    void testProxy_InvalidPaths(String requestUri, String expectedError) throws Exception {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("1");
        instance.setEndpoint("http://example.com");

        request.setRequestURI(requestUri);
        request.setMethod("GET");

        invokeProxy(turIntegrationAPI, instance, request, response);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
        assertEquals("{\"error\": \"" + expectedError + "\"}", response.getContentAsString());
    }
}
