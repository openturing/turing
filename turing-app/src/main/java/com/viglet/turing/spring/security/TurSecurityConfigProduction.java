/*
 * Copyright (C) 2016-2023 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.spring.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.stream.Stream;

import com.viglet.core.security.VigletCsrfCookieFilter;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.spring.security.auth.TurAuthTokenHeaderFilter;
import com.viglet.turing.spring.security.auth.TurGatewayKeyAuthFilter;
import com.viglet.turing.spring.security.auth.TurLogoutHandler;
import com.viglet.turing.spring.security.auth.TurSNSiteApiKeyGateFilter;
import com.viglet.turing.tenant.TurTenantResolutionFilter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
@EnableWebSecurity
@Profile("production")
@EnableMethodSecurity(securedEnabled = true)
@ComponentScan(basePackageClasses = TurCustomUserDetailsService.class)
public class TurSecurityConfigProduction {

    // --- S1192: extracted duplicated literals ---
    private static final String MCP = "/mcp/**";
    private static final String API_SIGNUP = "/api/signup";
    // T740 / §XLIX — OpenAI-compatible gateway (Block AZ). The /v1/* surface is a
    // server-to-server API (no browser cookie jar → CSRF-exempt) and is
    // permitAll here so authorization is enforced by the T741 virtual-key bearer
    // filter, not a session principal. The whole controller is
    // @ConditionalOnProperty(turing.gateway.enabled) — when off these paths 404.
    private static final String GATEWAY_V1 = "/v1/**";

        private static final String LOGIN_PATH = "/api/login";
        private static final String LOGOUT_PATH = "/logout";
        // OIDC Back-Channel Logout callback (Single Logout). Keycloak POSTs a signed
        // logout token here when the SSO session ends (e.g. logout from the Console),
        // and Spring invalidates the matching local session on its own. Server-to-
        // server, token-authenticated: no browser cookie/CSRF, so it must be CSRF-
        // exempt and permitAll (the logout token IS the credential). Path is Spring's
        // default: /logout/connect/back-channel/{registrationId} (registrationId=keycloak).
        private static final String BACK_CHANNEL_LOGOUT_PATH = "/logout/connect/back-channel/**";
        private static final String SETUP_PATH = "/api/setup";
        public static final String ERROR_PATH = "/error/**";

        // ── T894 / §XXXVII.18 — the anonymous surface, declared ONCE ────────────
        //
        // These three lists replace two hand-maintained copies of the same ~50
        // matchers, one per auth branch. Nothing held the copies equal, which is
        // what shipped `/api/sn/*/answer` into neither of them (T889) and `/sn/**`
        // into only one (T893). A new public endpoint is now added in one place and
        // cannot be half-added.
        //
        // The branches genuinely differ, so the difference is DECLARED rather than
        // left to emerge: PUBLIC_MATCHERS is what both permit, and the two
        // ..._ONLY_MATCHERS lists each say why they are not shared. Merging all
        // three into one list would sweep the admin console into the SSO branch —
        // a duplication bug traded for an authorization one.

        /**
         * Permitted in <strong>both</strong> auth modes: the genuinely public
         * surface. Anything added here is anonymous on every deployment, so an
         * entry needs a reason to be public, not merely a reason to be convenient.
         */
        private static final List<String> PUBLIC_MATCHERS = List.of(
                        ERROR_PATH,
                        "/api/discovery",
                        "/api/csrf",
                        "/api/v2/ping",
                        // Observability endpoints scraped by Prometheus and used by
                        // health checks. Restrict at network/firewall level in prod.
                        "/actuator/prometheus",
                        "/actuator/health",
                        "/actuator/health/**",
                        // T640 / §XXXVII.2 — /api/system/chat-analytics/** is NO longer
                        // permitAll: it exposed session enumeration + full chat
                        // transcripts (end-user PII) anonymously. It now falls under
                        // anyRequest().authenticated() and the controller adds
                        // @Secured({ROLE_ADMIN, AI_AGENT_VIEW}). Grafana's Infinity
                        // datasource must authenticate (basic/bearer).
                        "/assets/**",
                        "/favicon.ico",
                        "/*.png",
                        "/manifest.json",
                        "/swagger-resources/**",
                        "/browserconfig.xml",
                        "/api/sn/names",
                        "/api/sn/*/ac",
                        // T712 / §XLV — POST /api/sn/*/ac/query, the POST-native
                        // autocomplete variant. Same public, anonymous, read-only
                        // surface as the GET above.
                        "/api/sn/*/ac/**",
                        "/api/sn/*/search",
                        "/api/sn/*/search/**",
                        "/api/sn/*/_search",
                        "/api/sn/*/click",
                        "/api/sn/*/query",
                        "/api/sn/*/query/**",
                        "/api/sn/*/chat",
                        "/api/sn/*/chat/**",
                        // T889 / §LXIV.5 (Block BQ) — the T833 answer stream is the same
                        // anonymous LLM-cost class as the site chat above, so the SDK's
                        // public answer surfaces (showcase, public-site hero) can
                        // consume it keyless. It is gated by the same
                        // TurSNSiteApiKeyGateFilter and abuse-control caps — which
                        // T966 had to make true: this comment claimed it while the
                        // path was in neither filter's list, so an API_KEY-mode site's
                        // answer stream answered anyone. It is now declared in
                        // PUBLIC_SN_LLM_COST_MATCHERS and the coverage test holds both
                        // filters to it. `freshness` and `signal` ride
                        // with it: both are fire-and-forget companions of an answer
                        // already produced. `answer/share` is deliberately ABSENT —
                        // minting a permalink is a WRITE, and an anonymous write is a
                        // storage/spam surface. So is the `/answer/**` console page:
                        // T854 turns the reader defaults inside out there (rank, score,
                        // raw document), which is operator-only.
                        "/api/sn/*/answer",
                        "/api/sn/*/answer/freshness",
                        "/api/sn/*/answer/signal",
                        // T392 — grounded catalog copilot (LLM-cost surface;
                        // nginx-rate-limited on the demo host). Anonymous like chat so
                        // the model-catalog "Ask the catalog" widget can call it
                        // keyless; API_KEY-mode sites still gated by
                        // TurSNSiteApiKeyGateFilter.
                        "/api/sn/*/copilot",
                        "/api/sn/*/copilot/**",
                        // T1085 — a visitor's description parsed into the q + fq of
                        // the site's own search (LLM-cost surface, gated like chat).
                        "/api/sn/*/nl-filters",
                        // T634 — anonymous persona content-fit (LLM-cost surface;
                        // nginx-rate-limited on the demo host).
                        "/api/sn/*/persona/*/content-fit",
                        "/api/sn/*/*/spell-check",
                        "/api/ann/*/search",
                        // T119 — token-gated human-approval resolve endpoints (the
                        // opaque token is the capability). The plural
                        // /api/genai/approvals admin list stays authenticated.
                        "/api/genai/approval/*",
                        // T893 / §XXXVII.17 — the public faceted search page. It is
                        // served by TurSNSearchController (@RequestMapping("/sn")), a
                        // real controller and not a static resource, so it goes through
                        // this chain; without it anyRequest().authenticated() sent an
                        // anonymous visitor to SSO login for a page whose every API is
                        // already permitted here. The site's own SPA assets are served
                        // by the same controller under /sn/{site}/assets/**, so the
                        // /assets/** entry does not reach them either.
                        "/sn/**",
                        "/ann/**",
                        "/pages/**",
                        // T245 — MCP server endpoint (loopback-gated, see CSRF note below).
                        "/mcp",
                        MCP,
                        // T740 / §XLIX — gateway /v1/* (virtual-key auth, see note above).
                        GATEWAY_V1,
                        API_SIGNUP,
                        LOGIN_PATH,
                        SETUP_PATH
        );

        /**
         * T966 / §LXXV — the anonymous {@code /api/sn/**} surface, split by what a
         * call <b>costs</b>. Both lists together must equal every {@code /api/sn/}
         * entry in {@link #PUBLIC_MATCHERS}, and {@code TurLlmCostSurfaceCoverageTest}
         * enforces that — so a new public SN path cannot be added without saying
         * which of the two it is.
         *
         * <p>The classification is not documentation. Everything in
         * {@link #PUBLIC_SN_LLM_COST_MATCHERS} must be reached by both
         * {@code TurSNSiteApiKeyGateFilter} (so an API_KEY-mode site is not open)
         * and {@code TurAnonymousAbuseRateLimitFilter} (so an anonymous loop cannot
         * spend without bound), and the same test fails when it is not.
         *
         * <p>This exists because the claim used to live in a comment. T889 made the
         * answer stream anonymous "gated by the same TurSNSiteApiKeyGateFilter and
         * abuse-control caps" as chat, and it was in neither list.
         */
        private static final List<String> PUBLIC_SN_LLM_COST_MATCHERS = List.of(
                        "/api/sn/*/chat",
                        "/api/sn/*/chat/**",
                        "/api/sn/*/answer",
                        "/api/sn/*/answer/freshness",
                        "/api/sn/*/answer/signal",
                        "/api/sn/*/copilot",
                        "/api/sn/*/copilot/**",
                        "/api/sn/*/nl-filters",
                        "/api/sn/*/persona/*/content-fit"
        );

        /**
         * T966 — the anonymous SN paths that reach no model: retrieval and
         * telemetry. Throttling these in the abuse filter would break per-keystroke
         * autocomplete, so they are shed at the proxy with a looser zone instead
         * (§XXXVII.3). They still pass the API-key gate, which is about who may read
         * a site, not about what a call costs.
         */
        private static final List<String> PUBLIC_SN_CHEAP_MATCHERS = List.of(
                        "/api/sn/names",
                        "/api/sn/*/ac",
                        "/api/sn/*/ac/**",
                        "/api/sn/*/search",
                        "/api/sn/*/search/**",
                        "/api/sn/*/_search",
                        "/api/sn/*/click",
                        "/api/sn/*/query",
                        "/api/sn/*/query/**",
                        "/api/sn/*/*/spell-check"
        );

        /**
         * Permitted only under <strong>local</strong> auth. Under Keycloak these go
         * through SSO, which is the point of running Keycloak — the operator
         * surfaces below must NOT be moved into {@link #PUBLIC_MATCHERS}.
         */
        private static final List<String> LOCAL_AUTH_ONLY_MATCHERS = List.of(
                        // Operator surfaces. Anonymous here only because local auth
                        // lets the SPA boot and then authenticate against /api/login;
                        // under Keycloak the SSO redirect happens first, by design.
                        "/",
                        "/index.html",
                        "/welcome/**",
                        "/login/**",
                        "/setup/**",
                        "/admin/**",
                        "/bento/**",
                        "/console/**",
                        "/fonts/**",
                        // Local logout is a plain GET on this app; under Keycloak
                        // logout is redirected to the provider instead (see below).
                        LOGOUT_PATH,
                        "/api/v2/guest/**",
                        "/api/v2/user/register"
        );

        /**
         * Permitted only under <strong>Keycloak</strong>. The OIDC Back-Channel
         * Logout callback is authenticated by the provider's signed logout token
         * rather than a session, and means nothing without a provider — so it is
         * deliberately not part of the shared surface.
         */
        private static final List<String> KEYCLOAK_ONLY_MATCHERS = List.of(
                        BACK_CHANNEL_LOGOUT_PATH
        );

        private final UserDetailsService userDetailsService;

        private final PasswordEncoder passwordEncoder;
        @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:''}")
        private String issuerUri;
        @Value("${spring.security.oauth2.client.registration.keycloak.client-id:''}")
        private String clientId;
        @Value("${turing.url:'http://localhost:2700'}")
        private String turingUrl;
        @Value("${server.servlet.session.cookie.name:JSESSIONID}")
        private String sessionCookieName;
        PathPatternRequestMatcher.Builder mvc = PathPatternRequestMatcher.withDefaults();

        /**
         * Turns the declared path groups into the matchers {@code requestMatchers}
         * wants (T894 / §XXXVII.18). Taking the groups as varargs is what lets each
         * branch spell its own surface as {@code PUBLIC + <its own>} instead of
         * restating the shared half.
         */
        private RequestMatcher[] matchers(List<String> shared, List<String> branchOnly) {
                return Stream.concat(shared.stream(), branchOnly.stream())
                                .map(mvc::matcher)
                                .toArray(RequestMatcher[]::new);
        }

        /**
         * The declared surfaces, for the source-level guards in
         * {@code TurPublicSearchPageAuthClassTest} / {@code TurAnswerSurfaceAuthClassTest}
         * to assert against. Immutable {@code List.of(...)}, not arrays: an
         * allowlist a caller could mutate in place is not an allowlist.
         */
        static List<String> publicMatchers() {
                return PUBLIC_MATCHERS;
        }

        static List<String> localAuthOnlyMatchers() {
                return LOCAL_AUTH_ONLY_MATCHERS;
        }

        /** T966 / §LXXV — read by {@code TurLlmCostSurfaceCoverageTest}. */
        static List<String> publicSnLlmCostMatchers() {
                return PUBLIC_SN_LLM_COST_MATCHERS;
        }

        static List<String> publicSnCheapMatchers() {
                return PUBLIC_SN_CHEAP_MATCHERS;
        }

        static List<String> keycloakOnlyMatchers() {
                return KEYCLOAK_ONLY_MATCHERS;
        }

        public TurSecurityConfigProduction(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
                this.userDetailsService = userDetailsService;
                this.passwordEncoder = passwordEncoder;
        }

        @Bean
        @SuppressWarnings("java:S4502")
        SecurityFilterChain filterChain(HttpSecurity http,
                        TurAuthTokenHeaderFilter turAuthTokenHeaderFilter,
                        TurGatewayKeyAuthFilter turGatewayKeyAuthFilter,
                        TurSNSiteApiKeyGateFilter turSNSiteApiKeyGateFilter,
                        TurTenantResolutionFilter turTenantResolutionFilter,
                        TurLogoutHandler turLogoutHandler,
                        TurConfigProperties turConfigProperties,
                        TurAuthenticationEntryPoint turAuthenticationEntryPoint,
                        TurOAuth2UserService turOAuth2UserService,
                        TurOidcUserService turOidcUserService,
                        ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {

                http.headers(header -> header.frameOptions(
                                frameOptions -> frameOptions.disable()
                                                .cacheControl(HeadersConfigurer.CacheControlConfig::disable)));
                http.cors(Customizer.withDefaults());
                http.addFilterBefore(turAuthTokenHeaderFilter, BasicAuthenticationFilter.class);
                // T741 / §XLIX — authenticate the gateway /v1/* surface with a
                // virtual key (Authorization: Bearer sk-turing-...). No-op off /v1/*
                // and when the gateway is disabled.
                http.addFilterBefore(turGatewayKeyAuthFilter, BasicAuthenticationFilter.class);
                // T233 / §VII.6.h — runs after the Dev-Token filter so a valid `Key`
                // header is already authenticated; gates per-site API_KEY mode on the
                // public SN surface (PUBLIC sites pass straight through).
                http.addFilterAfter(turSNSiteApiKeyGateFilter, TurAuthTokenHeaderFilter.class);
                // T259 / §XIV.2.3 — resolve + bind the current tenant once the
                // principal is known. No-op when turing.tenancy.enabled=false.
                http.addFilterAfter(turTenantResolutionFilter, TurSNSiteApiKeyGateFilter.class);
                http.userDetailsService(userDetailsService);
                http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
                http.securityContext(securityContext -> securityContext
                                .securityContextRepository(new HttpSessionSecurityContextRepository()));
                http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

                CookieCsrfTokenRepository csrfTokenRepository = new CookieCsrfTokenRepository();

                http.csrf(csrf -> csrf
                                .csrfTokenRepository(csrfTokenRepository)
                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                                .ignoringRequestMatchers(
                                                // T966 / §LXXV — `/api/genai/chat` used to be listed
                                                // here. No controller maps it, and `/api/genai/**`
                                                // below already covers the prefix, so the line
                                                // exempted a route that does not exist — the kind of
                                                // leftover that stops being harmless the day somebody
                                                // adds that path back expecting CSRF to apply.
                                                mvc.matcher("/api/v2/integration/**"),
                                                // AI Agent chat consumed server-to-server (e.g. the
                                                // viglet.com Next.js proxy) authenticated by a Dev Token
                                                // in the `Key` header — no browser cookie jar, so no CSRF
                                                // token can round-trip. Auth still required (endpoint is
                                                // NOT in permitAll); only CSRF is skipped, mirroring the
                                                // site chat under /api/sn/**.
                                                mvc.matcher("/api/v2/ai-agent/*/chat"),
                                                mvc.matcher("/api/v2/ai-agent/*/chat/**"),
                                                mvc.matcher("/api/v2/ai-agent/*/chat-flow-state"),
                                                mvc.matcher("/api/sn/**"),
                                                mvc.matcher("/api/ann/**"),
                                                mvc.matcher("/graphql"),
                                                mvc.matcher(ERROR_PATH),
                                                mvc.matcher(LOGOUT_PATH),
                                                mvc.matcher(BACK_CHANNEL_LOGOUT_PATH),
                                                mvc.matcher("/api/ocr/**"),
                                                mvc.matcher("/api/genai/**"),
                                                mvc.matcher(GATEWAY_V1),
                                                // T245 — MCP server (Streamable HTTP). Not a
                                                // browser surface (no cookie jar); auth/loopback is
                                                // enforced by TurMcpLoopbackFilter today and the
                                                // T246 OAuth 2.1 resource-server gate next.
                                                mvc.matcher("/mcp"),
                                                mvc.matcher(MCP),
                                                mvc.matcher("/api/v2/guest/**"),
                                                mvc.matcher("/h2/**"),
                                                mvc.matcher("/git/**"),
                                                mvc.matcher(LOGIN_PATH),
                                                mvc.matcher(SETUP_PATH),
                                                mvc.matcher("/api/v2/user/register"),
                                                mvc.matcher(API_SIGNUP),
                                                mvc.matcher("/login/oauth2/**"),
                                                mvc.matcher("/oauth2/**")))
                                .addFilterAfter(new VigletCsrfCookieFilter(), BasicAuthenticationFilter.class);
                boolean oauth2Available = clientRegistrationRepository.getIfAvailable() != null;
                if (turConfigProperties.isKeycloak()) {
                        String keycloakUrlFormat = String.format(
                                        "%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                                        issuerUri, clientId, turingUrl);
                        if (oauth2Available) {
                                http.oauth2Login(oauth2 -> oauth2.userInfoEndpoint(
                                                userInfo -> userInfo
                                                        .userService(turOAuth2UserService)
                                                        .oidcUserService(turOidcUserService)));
                                // Single Logout: accept Keycloak's OIDC Back-Channel Logout so a
                                // logout at the Console (which ends the shared SSO session) cascades
                                // here — turing invalidates its own session without the user having
                                // to visit /logout. Requires this app's own Keycloak client
                                // (viglet-turing / per-silo viglet-turing-<key>) with a Backchannel
                                // Logout URL; the shared viglet-cloud client cannot fan out to every app.
                                http.oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()));
                        }
                        http.authorizeHttpRequests(authorizeRequests -> {
                                authorizeRequests.requestMatchers(
                                                matchers(PUBLIC_MATCHERS, KEYCLOAK_ONLY_MATCHERS))
                                                .permitAll();
                                authorizeRequests.anyRequest().authenticated();
                        });
                        http.logout(logout -> logout.addLogoutHandler(turLogoutHandler)
                                        .logoutSuccessUrl(keycloakUrlFormat));
                } else {
                        if (oauth2Available
                                        && (turConfigProperties.getAuthentication() == null
                                        || turConfigProperties.getAuthentication().isThirdparty())) {
                                http.oauth2Login(oauth2 -> oauth2
                                                .loginPage("/login")
                                                .defaultSuccessUrl("/admin", true)
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(turOAuth2UserService)
                                                                .oidcUserService(turOidcUserService)));
                        }
                        http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(turAuthenticationEntryPoint))
                                        .authorizeHttpRequests(authorizeRequests -> {
                                                authorizeRequests.requestMatchers(
                                                                matchers(PUBLIC_MATCHERS,
                                                                                LOCAL_AUTH_ONLY_MATCHERS))
                                                                .permitAll();
                                                authorizeRequests.anyRequest().authenticated();

                                        });
                        http.logout(logout -> logout
                                        .logoutRequestMatcher(mvc.matcher(HttpMethod.GET, LOGOUT_PATH))
                                        .addLogoutHandler(turLogoutHandler)
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies(sessionCookieName, "XSRF-TOKEN")
                                        .logoutSuccessUrl("/login"));
                }
                return http.build();
        }

        @Bean
        WebSecurityCustomizer webSecurityCustomizer() {
                // T652 / §XXXVII.14 — keep the URL-encoded-slash firewall but DROP the
                // former `.ignoring().requestMatchers("/h2/**")` bypass, which disabled
                // the entire security filter chain for /h2/**. The H2 console is off by
                // default (and now loopback-only); if enabled it goes through the normal
                // security chain instead of being an unauthenticated hole.
                return web -> web.httpFirewall(allowUrlEncodedSlaturHttpFirewall());
        }

        @Bean
        AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) {
                try {
                        return authenticationConfiguration.getAuthenticationManager();
                } catch (Exception e) {
                        throw new IllegalStateException("Failed to get AuthenticationManager", e);
                }
        }

        @Autowired
        public void configureGlobal(AuthenticationManagerBuilder auth) {
                auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
        }

        @Bean
        HttpFirewall allowUrlEncodedSlaturHttpFirewall() {
                // Allow double slash in URL
                StrictHttpFirewall firewall = new StrictHttpFirewall();
                firewall.setAllowUrlEncodedSlash(true);
                return firewall;
        }
}
