package com.viglet.turing.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.spring.security.TurAuditorAwareImpl;

class TurJpaConfigTest {

    @Test
    void shouldCreateAuditorAwareBeanBackedByTurAuditorAwareImpl() {
        TurConfigProperties properties = new TurConfigProperties();
        properties.setKeycloak(false);

        TurJpaConfig config = new TurJpaConfig(properties);
        AuditorAware<String> auditorAware = config.auditorAware();

        assertNotNull(auditorAware);
        assertTrue(auditorAware instanceof TurAuditorAwareImpl);
        assertEquals("admin", auditorAware.getCurrentAuditor().orElseThrow());
    }
}
