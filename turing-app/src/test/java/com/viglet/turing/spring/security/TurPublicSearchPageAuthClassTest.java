package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T893 / §XXXVII.17 (Block AN) — the public faceted search page is anonymous
 * under Keycloak too, not only under local auth.
 *
 * <p>Guarded over the source for the reason
 * {@link TurAnswerSurfaceAuthClassTest} states: {@code TurSecurityConfigProduction}
 * is {@code @Profile("production")}, so nothing in this suite loads it and no
 * MockMvc call can prove what it permits. The two branches each carry their own
 * copy of the permitAll list, which is the asymmetry that produced this bug, so
 * every assertion here counts both.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPublicSearchPageAuthClassTest {

    private static final Path CONFIG = Path.of(
            "src/main/java/com/viglet/turing/spring/security/TurSecurityConfigProduction.java");

    /** How many permitAll branches the config declares — one per auth mode. */
    private static final int BRANCHES = 2;

    private static final Pattern MATCHER =
            Pattern.compile("mvc\\.matcher\\(\\s*\"([^\"]+)\"\\s*\\)");

    private static String source;

    @BeforeAll
    static void readConfig() throws IOException {
        assertThat(CONFIG).as("the production security config must be where this test looks")
                .exists();
        source = Files.readString(CONFIG, StandardCharsets.UTF_8);
    }

    private static int matcherCount(String path) {
        return source.split(Pattern.quote("mvc.matcher(\"" + path + "\")"), -1).length - 1;
    }

    /** The paths one branch's permitAll list names, by branch marker. */
    private static Set<String> permitted(String branchStart) {
        int start = source.indexOf(branchStart);
        assertThat(start).as("branch marker not found: " + branchStart).isNotNegative();
        int end = source.indexOf("authorizeRequests.anyRequest().authenticated();", start);
        assertThat(end).as("branch has no anyRequest() terminator").isGreaterThan(start);
        Set<String> paths = new TreeSet<>();
        Matcher m = MATCHER.matcher(source.substring(start, end));
        while (m.find()) {
            paths.add(m.group(1));
        }
        return paths;
    }

    private static Set<String> keycloakBranch() {
        return permitted("if (turConfigProperties.isKeycloak())");
    }

    private static Set<String> localBranch() {
        return permitted("http.httpBasic(httpBasic ->");
    }

    @Test
    void thePublicSearchPageIsAnonymousInBothBranches() {
        assertThat(matcherCount("/sn/**"))
                .as("the flagship anonymous surface — a Keycloak deployment must not send "
                        + "a visitor to SSO login for it")
                .isEqualTo(BRANCHES);
    }

    @Test
    void theSearchPageIsPermittedWhereverItsOwnApisAre() {
        // The page and the data it reads are one anonymous surface: permitting the
        // APIs and not the page serving them is the exact shape of this defect.
        for (Set<String> branch : Set.of(keycloakBranch(), localBranch())) {
            assertThat(branch)
                    .as("a branch permitting the search APIs must permit the page too")
                    .contains("/api/sn/*/search", "/api/sn/*/ac", "/api/sn/*/click", "/sn/**");
        }
    }

    @Test
    void theSearchPageSitsBesideTheOtherTwoPublicSpaRoutes() {
        // /ann/** and /pages/** were already in both branches; /sn/** is the same
        // class of route and was the only one of the three missing from Keycloak.
        assertThat(matcherCount("/ann/**")).isEqualTo(BRANCHES);
        assertThat(matcherCount("/pages/**")).isEqualTo(BRANCHES);
    }

    @Test
    void theConsoleStaysBehindSsoUnderKeycloak() {
        // The counterweight: this task adds ONE matcher. The operator surfaces are
        // local-branch-only on purpose — under Keycloak they must go through SSO,
        // so a future "make the two lists equal" must not sweep these across.
        assertThat(keycloakBranch())
                .as("the admin console is not a public page")
                .doesNotContain("/admin/**", "/bento/**", "/console/**", "/setup/**");
    }

    @Test
    void theKeycloakBranchNeverPermitsMoreThanTheLocalOne() {
        // Whatever the two lists disagree about, the SSO branch stays the narrower
        // of the two: an entry present only under Keycloak would be a hole that the
        // more permissive local mode does not even have.
        assertThat(keycloakBranch()).isSubsetOf(localBranch());
    }
}
