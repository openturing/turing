package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Public chat / AI Mode configuration exposed to the frontend SDK.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurChatProperty {

    private TurChatSessionProperty session = new TurChatSessionProperty();

    @Getter
    @Setter
    public static class TurChatSessionProperty {
        /**
         * Name of the cookie minted by the React SDK to identify a returning
         * visitor. Override when the host site already reserves the default
         * name for another purpose, or to namespace per-deployment.
         * Default: {@code TUR_SESSION}.
         */
        private String cookieName = "TUR_SESSION";
        /**
         * Lifetime of the session cookie. The same value is reused as the
         * chat {@code conversationId}, so this also bounds how long a
         * conversation can be resumed without resetting flow state.
         * Default: 30 days.
         */
        private long ttlSeconds = 2_592_000L;
    }
}
