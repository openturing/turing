package com.viglet.turing.tomcat;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

class TurTomcatWebServerCustomizerTest {

    private final TurTomcatWebServerCustomizer customizer = new TurTomcatWebServerCustomizer();

    @Test
    void shouldRegisterConnectorCustomizerAndSetRelaxedProperties() {
        TomcatServletWebServerFactory factory = mock(TomcatServletWebServerFactory.class);

        customizer.customize(factory);

        ArgumentCaptor<TomcatConnectorCustomizer[]> captor = ArgumentCaptor.forClass(TomcatConnectorCustomizer[].class);
        verify(factory).addConnectorCustomizers(captor.capture());

        TomcatConnectorCustomizer[] customizers = captor.getValue();
        assertNotNull(customizers);
        assertTrue(customizers.length > 0);

        Connector connector = mock(Connector.class);
        customizers[0].customize(connector);

        verify(connector).setProperty("relaxedPathChars", "<>[\\]^`{|}");
        verify(connector).setProperty("relaxedQueryChars", "<>[\\]^`{|}");
    }
}
