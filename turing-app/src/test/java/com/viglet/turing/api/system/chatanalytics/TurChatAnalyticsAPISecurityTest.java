package com.viglet.turing.api.system.chatanalytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.annotation.Secured;

/**
 * Guards the T640 / §XXXVII.2 fix: the chat-analytics read API (session
 * enumeration + full chat transcripts / PII) must require an authenticated
 * admin and must NOT be reachable anonymously. A reflection check keeps the
 * class-level {@link Secured} annotation from silently regressing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurChatAnalyticsAPISecurityTest {

    @Test
    void controllerRequiresAdminRole() {
        Secured secured = TurChatAnalyticsAPI.class.getAnnotation(Secured.class);

        assertThat(secured)
                .as("TurChatAnalyticsAPI must be @Secured (T640 / §XXXVII.2)")
                .isNotNull();
        assertThat(secured.value()).contains("ROLE_ADMIN");
    }
}
