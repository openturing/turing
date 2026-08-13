package com.viglet.turing.spring.security;

import static com.viglet.turing.spring.security.TurSecurityConfigProduction.publicMatchers;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T889 / §LXIV.5 (Block BQ) — guards the answer surface's auth class.
 *
 * <p>{@code TurSecurityConfigProduction} is {@code @Profile("production")}, so no
 * integration test in this suite loads it and no MockMvc call can prove what it
 * permits. The guard is therefore over its declaration: the answer <em>stream</em>
 * is anonymous like the site chat beside it, while minting a share permalink (a
 * write) and the {@code /answer/**} operator page are not.
 *
 * <p>Originally these assertions counted {@code mvc.matcher("…")} occurrences and
 * required two, because the permitAll list was spelled once per auth branch and a
 * shipped endpoint could land in neither. T894 replaced the two copies with one
 * declared {@code PUBLIC_MATCHERS}, so counting is no longer the check — being in
 * the shared list is, which is both stronger and what §XXXVII.18 asked for.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerSurfaceAuthClassTest {

    private static final Path CONFIG = Path.of(
            "src/main/java/com/viglet/turing/spring/security/TurSecurityConfigProduction.java");

    private static String source;

    @BeforeAll
    static void readConfig() throws IOException {
        assertThat(CONFIG).as("the production security config must be where this test looks")
                .exists();
        source = Files.readString(CONFIG, StandardCharsets.UTF_8);
    }

    @Test
    void answerStreamIsAnonymousOnEveryDeployment() {
        assertThat(publicMatchers())
                .as("the T833 answer stream is the same anonymous LLM-cost class as site chat")
                .contains("/api/sn/*/answer");
    }

    @Test
    void freshnessAndSignalRideWithTheStream() {
        assertThat(publicMatchers())
                .as("T840 freshness and T845 signals are fire-and-forget companions of an "
                        + "answer the public surface already produced")
                .contains("/api/sn/*/answer/freshness", "/api/sn/*/answer/signal");
    }

    @Test
    void mintingASharePermalinkIsNeverAnonymous() {
        assertThat(publicMatchers())
                .as("minting a permalink is a WRITE: an anonymous one is a storage/spam surface")
                .doesNotContain("/api/sn/*/answer/share", "/api/sn/*/answer/**");
    }

    @Test
    void theOperatorPageIsNeverAnonymous() {
        assertThat(publicMatchers())
                .as("T854 exposes rank, score and the raw document on /answer/** — operator-only")
                .doesNotContain("/answer/**");
    }

    @Test
    void theAnswerPathsAreCsrfExemptByTheExistingSnRule() {
        // The POST companions need no CSRF entry of their own: /api/sn/** is already
        // ignored wholesale. Still a source assertion — the CSRF ignore list is one
        // list already (it was never per-branch), so T894 left it alone.
        assertThat(source)
                .as("/api/sn/** must stay in the CSRF ignore list for the answer POSTs to work")
                .contains("mvc.matcher(\"/api/sn/**\")");
    }
}
