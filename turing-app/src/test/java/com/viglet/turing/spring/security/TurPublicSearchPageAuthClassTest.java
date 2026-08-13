package com.viglet.turing.spring.security;

import static com.viglet.turing.spring.security.TurSecurityConfigProduction.keycloakOnlyMatchers;
import static com.viglet.turing.spring.security.TurSecurityConfigProduction.localAuthOnlyMatchers;
import static com.viglet.turing.spring.security.TurSecurityConfigProduction.publicMatchers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T893 / §XXXVII.17 and T894 / §XXXVII.18 (Block AN) — the anonymous surface is
 * declared once, the two branches' differences are declared on purpose, and the
 * public faceted search page is in the shared half.
 *
 * <p>{@code TurSecurityConfigProduction} is {@code @Profile("production")}, so
 * nothing in this suite loads it and no MockMvc call can prove what it permits.
 * Before T894 the guard therefore counted {@code mvc.matcher("…")} occurrences in
 * the source and asserted two — a check the section itself called weaker, because
 * a refactor satisfies it without meaning anything. Now that one list is the
 * single declaration, these assertions read it directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPublicSearchPageAuthClassTest {

    /** Everything an anonymous request may reach under Keycloak. */
    private static List<String> keycloakSurface() {
        List<String> all = new ArrayList<>(publicMatchers());
        all.addAll(keycloakOnlyMatchers());
        return all;
    }

    /** Everything an anonymous request may reach under local auth. */
    private static List<String> localSurface() {
        List<String> all = new ArrayList<>(publicMatchers());
        all.addAll(localAuthOnlyMatchers());
        return all;
    }

    // ---- T893: the public search page ---------------------------------------

    @Test
    void thePublicSearchPageIsInTheSharedSurface() {
        assertThat(publicMatchers())
                .as("the flagship anonymous surface — no deployment mode may send a "
                        + "visitor to a login for it")
                .contains("/sn/**");
        assertThat(keycloakSurface()).contains("/sn/**");
        assertThat(localSurface()).contains("/sn/**");
    }

    @Test
    void theSearchPageIsPermittedWhereverItsOwnApisAre() {
        // The page and the data it reads are one anonymous surface: permitting the
        // APIs and not the page serving them is the exact shape of the T893 defect.
        assertThat(publicMatchers())
                .contains("/api/sn/*/search", "/api/sn/*/ac", "/api/sn/*/click", "/sn/**");
    }

    @Test
    void theOtherTwoPublicSpaRoutesAreSharedToo() {
        assertThat(publicMatchers()).contains("/ann/**", "/pages/**");
    }

    // ---- T894: the difference is declared, not emergent ---------------------

    @Test
    void theConsoleIsLocalAuthOnlyAndNeverShared() {
        // Under Keycloak the operator surfaces must go through SSO. This is the
        // assertion that stops "make the two lists equal" from becoming an
        // authorization bug.
        assertThat(localAuthOnlyMatchers())
                .contains("/admin/**", "/bento/**", "/console/**", "/setup/**");
        assertThat(publicMatchers())
                .as("the admin console is not a public page")
                .doesNotContain("/admin/**", "/bento/**", "/console/**", "/setup/**");
        assertThat(keycloakSurface())
                .doesNotContain("/admin/**", "/bento/**", "/console/**", "/setup/**");
    }

    @Test
    void theOnlyKeycloakOnlyEntryIsTheBackChannelLogoutCallback() {
        // Corrects what the pre-T894 guard could not see: it matched only string
        // literals, so the constant-named BACK_CHANNEL_LOGOUT_PATH was invisible and
        // a "Keycloak never permits more than local" assertion passed vacuously.
        // The SSO branch does permit one thing local does not, and it is this.
        assertThat(keycloakOnlyMatchers())
                .containsExactly("/logout/connect/back-channel/**");
        assertThat(localSurface())
                .as("a provider callback means nothing without a provider")
                .doesNotContain("/logout/connect/back-channel/**");
    }

    @Test
    void thePublicSurfaceAndTheBranchOnlyOnesDoNotOverlap() {
        // An entry in both a shared list and a branch-only one would mean the
        // difference is no longer declared by the lists' names.
        assertThat(publicMatchers()).doesNotContainAnyElementsOf(localAuthOnlyMatchers());
        assertThat(publicMatchers()).doesNotContainAnyElementsOf(keycloakOnlyMatchers());
        assertThat(localAuthOnlyMatchers())
                .doesNotContainAnyElementsOf(keycloakOnlyMatchers());
    }

    @Test
    void noSurfaceListRepeatsAPath() {
        assertThat(publicMatchers()).doesNotHaveDuplicates();
        assertThat(localAuthOnlyMatchers()).doesNotHaveDuplicates();
        assertThat(keycloakOnlyMatchers()).doesNotHaveDuplicates();
    }

    @Test
    void theSurfaceListsCannotBeMutatedInPlace() {
        // An allowlist a caller could edit at runtime is not an allowlist. This is
        // why the declaration is List.of(...) and not String[].
        assertThatThrownBy(() -> publicMatchers().add("/anything"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> localAuthOnlyMatchers().add("/anything"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> keycloakOnlyMatchers().add("/anything"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * The bridge between the declaration and its use. Every assertion above reads
     * the lists; none of them can see whether a branch still <em>passes</em> them
     * to {@code requestMatchers}. Without this, rewiring one branch to some other
     * list reinstates the T889/T893 drift with the whole guard staying green — a
     * gap found by injecting exactly that.
     */
    @Test
    void bothBranchesBuildTheirSurfaceFromTheSharedList() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/viglet/turing/spring/"
                + "security/TurSecurityConfigProduction.java"), StandardCharsets.UTF_8);

        assertThat(source)
                .as("the Keycloak branch must permit the shared surface plus its own")
                .contains("matchers(PUBLIC_MATCHERS, KEYCLOAK_ONLY_MATCHERS)");
        assertThat(source)
                .as("the local branch must permit the shared surface plus its own")
                .contains("matchers(PUBLIC_MATCHERS,")
                .contains("LOCAL_AUTH_ONLY_MATCHERS)");
        assertThat(source.split(java.util.regex.Pattern.quote("matchers(PUBLIC_MATCHERS"), -1)
                .length - 1)
                .as("exactly two branches, each built from the shared list")
                .isEqualTo(2);
        assertThat(source)
                .as("no branch may hand requestMatchers a list assembled inline — that is "
                        + "how the two hand-maintained copies came back")
                .doesNotContain("matchers(List.of(");
    }

    @Test
    void nothingSensitiveSlippedIntoTheSharedSurface() {
        // The paths past audits explicitly took OUT of permitAll. Asserted against
        // the one declaration now, so a re-add cannot hide in one branch.
        assertThat(publicMatchers())
                .doesNotContain("/api/system/chat-analytics/**",
                        "/api/sn/*/answer/share",
                        "/api/sn/*/answer/**",
                        "/answer/**",
                        "/h2/**",
                        "/api/genai/approvals");
    }
}
