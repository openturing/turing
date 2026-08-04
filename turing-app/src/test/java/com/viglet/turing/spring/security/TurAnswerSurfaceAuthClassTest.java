package com.viglet.turing.spring.security;

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
 * permits. The guard is therefore over its source: the answer <em>stream</em> is
 * anonymous like the site chat beside it, while minting a share permalink (a
 * write) and the {@code /answer/**} operator page are not. Each of the two
 * branches — Keycloak and local — carries its own copy of the list, which is
 * exactly the asymmetry that let this drift in the first place, so every
 * assertion here counts both.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAnswerSurfaceAuthClassTest {

    private static final Path CONFIG = Path.of(
            "src/main/java/com/viglet/turing/spring/security/TurSecurityConfigProduction.java");

    /** How many permitAll branches the config declares — one per auth mode. */
    private static final int BRANCHES = 2;

    private static String source;

    @BeforeAll
    static void readConfig() throws IOException {
        assertThat(CONFIG).as("the production security config must be where this test looks").exists();
        source = Files.readString(CONFIG, StandardCharsets.UTF_8);
    }

    private static int matcherCount(String path) {
        return source.split(java.util.regex.Pattern.quote("mvc.matcher(\"" + path + "\")"), -1).length - 1;
    }

    @Test
    void answerStreamIsAnonymousInBothBranches() {
        assertThat(matcherCount("/api/sn/*/answer"))
                .as("the T833 answer stream is the same anonymous LLM-cost class as site chat")
                .isEqualTo(BRANCHES);
    }

    @Test
    void freshnessAndSignalRideWithTheStream() {
        assertThat(matcherCount("/api/sn/*/answer/freshness"))
                .as("T840 freshness is a fire-and-forget companion of a produced answer")
                .isEqualTo(BRANCHES);
        assertThat(matcherCount("/api/sn/*/answer/signal"))
                .as("T845 signals are emitted by the public answer surface too")
                .isEqualTo(BRANCHES);
    }

    @Test
    void mintingASharePermalinkIsNeverAnonymous() {
        assertThat(source)
                .as("minting a permalink is a WRITE: an anonymous one is a storage/spam surface")
                .doesNotContain("mvc.matcher(\"/api/sn/*/answer/share\")")
                .doesNotContain("mvc.matcher(\"/api/sn/*/answer/**\")");
    }

    @Test
    void theOperatorPageIsNeverAnonymous() {
        assertThat(source)
                .as("T854 exposes rank, score and the raw document on /answer/** — operator-only")
                .doesNotContain("mvc.matcher(\"/answer/**\")");
    }

    @Test
    void theAnswerPathsAreCsrfExemptByTheExistingSnRule() {
        // The POST companions need no CSRF entry of their own: /api/sn/** is already
        // ignored wholesale. Asserted so removing that line surfaces here, not in prod.
        assertThat(source)
                .as("/api/sn/** must stay in the CSRF ignore list for the answer POSTs to work")
                .contains("mvc.matcher(\"/api/sn/**\")");
    }
}
