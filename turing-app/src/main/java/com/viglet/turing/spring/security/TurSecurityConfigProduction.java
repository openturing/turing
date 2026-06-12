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

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.spring.security.auth.TurAuthTokenHeaderFilter;
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
        private static final String LOGIN_PATH = "/api/login";
        private static final String LOGOUT_PATH = "/logout";
        private static final String SETUP_PATH = "/api/setup";
        public static final String ERROR_PATH = "/error/**";
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

        public TurSecurityConfigProduction(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
                this.userDetailsService = userDetailsService;
                this.passwordEncoder = passwordEncoder;
        }

        @Bean
        @SuppressWarnings("java:S4502")
        SecurityFilterChain filterChain(HttpSecurity http,
                        TurAuthTokenHeaderFilter turAuthTokenHeaderFilter,
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
                                                mvc.matcher("/api/genai/chat"),
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
                                                mvc.matcher("/api/ocr/**"),
                                                mvc.matcher("/api/genai/**"),
                                                // T245 — MCP server (Streamable HTTP). Not a
                                                // browser surface (no cookie jar); auth/loopback is
                                                // enforced by TurMcpLoopbackFilter today and the
                                                // T246 OAuth 2.1 resource-server gate next.
                                                mvc.matcher("/mcp"),
                                                mvc.matcher("/mcp/**"),
                                                mvc.matcher("/api/v2/guest/**"),
                                                mvc.matcher("/h2/**"),
                                                mvc.matcher("/git/**"),
                                                mvc.matcher(LOGIN_PATH),
                                                mvc.matcher(SETUP_PATH),
                                                mvc.matcher("/api/v2/user/register"),
                                                mvc.matcher("/api/signup"),
                                                mvc.matcher("/login/oauth2/**"),
                                                mvc.matcher("/oauth2/**")))
                                .addFilterAfter(new TurCsrfCookieFilter(), BasicAuthenticationFilter.class);
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
                        }
                        http.authorizeHttpRequests(authorizeRequests -> {
                                authorizeRequests.requestMatchers(
                                                mvc.matcher(ERROR_PATH),
                                                mvc.matcher("/api/discovery"),
                                                mvc.matcher("/api/csrf"),
                                                mvc.matcher("/api/v2/ping"),
                                                // Observability endpoints scraped by Prometheus and used by
                                                // health checks. Restrict at network/firewall level in prod.
                                                mvc.matcher("/actuator/prometheus"),
                                                mvc.matcher("/actuator/health"),
                                                mvc.matcher("/actuator/health/**"),
                                                // Read-only chat analytics API consumed by Grafana
                                                // via the Infinity datasource. Restrict at network
                                                // level in prod (same model as /actuator/prometheus).
                                                mvc.matcher("/api/system/chat-analytics/**"),
                                                mvc.matcher("/assets/**"),
                                                mvc.matcher("/favicon.ico"),
                                                mvc.matcher("/*.png"),
                                                mvc.matcher("/manifest.json"),
                                                mvc.matcher("/swagger-resources/**"),
                                                mvc.matcher("/browserconfig.xml"),
                                                mvc.matcher("/api/sn/names"),
                                                mvc.matcher("/api/sn/*/ac"),
                                                mvc.matcher("/api/sn/*/search"),
                                                mvc.matcher("/api/sn/*/search/**"),
                                                mvc.matcher("/api/sn/*/_search"),
                                                mvc.matcher("/api/sn/*/click"),
                                                mvc.matcher("/api/sn/*/query"),
                                                mvc.matcher("/api/sn/*/query/**"),
                                                mvc.matcher("/api/sn/*/chat"),
                                                mvc.matcher("/api/sn/*/chat/**"),
                                                mvc.matcher("/api/sn/*/*/spell-check"),
                                                mvc.matcher("/api/ann/*/search"),
                                                mvc.matcher("/ann/**"),
                                                mvc.matcher("/pages/**"),
                                                // T245 — MCP server endpoint (loopback-gated, see CSRF note above).
                                                mvc.matcher("/mcp"),
                                                mvc.matcher("/mcp/**"),
                                                mvc.matcher("/api/signup"),
                                                mvc.matcher(LOGIN_PATH),
                                                mvc.matcher(SETUP_PATH)).permitAll();
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
                                                                mvc.matcher(ERROR_PATH),
                                                                mvc.matcher("/api/discovery"),
                                                                mvc.matcher("/api/csrf"),
                                                                mvc.matcher("/api/v2/ping"),
                                                                // Observability endpoints scraped by Prometheus
                                                                // and used by health checks. Restrict at
                                                                // network/firewall level in prod.
                                                                mvc.matcher("/actuator/prometheus"),
                                                                mvc.matcher("/actuator/health"),
                                                                mvc.matcher("/actuator/health/**"),
                                                                // Read-only chat analytics API consumed by Grafana
                                                                // via the Infinity datasource. Restrict at network
                                                                // level in prod (same model as /actuator/prometheus).
                                                                mvc.matcher("/api/system/chat-analytics/**"),
                                                                mvc.matcher(LOGOUT_PATH),
                                                                mvc.matcher("/index.html"),
                                                                mvc.matcher("/welcome/**"),
                                                                mvc.matcher("/login/**"),
                                                                mvc.matcher("/setup/**"),
                                                                mvc.matcher("/admin/**"),
                                                                mvc.matcher("/bento/**"),
                                                                mvc.matcher("/"),
                                                                mvc.matcher("/assets/**"),
                                                                mvc.matcher("/swagger-resources/**"),
                                                                mvc.matcher("/sn/**"),
                                                                mvc.matcher("/fonts/**"),
                                                                mvc.matcher("/api/sn/names"),
                                                                mvc.matcher("/api/sn/*/ac"),
                                                                mvc.matcher("/api/sn/*/search"),
                                                                mvc.matcher("/api/sn/*/search/**"),
                                                                mvc.matcher("/api/sn/*/_search"),
                                                mvc.matcher("/api/sn/*/click"),
                                                                mvc.matcher("/api/sn/*/query"),
                                                                mvc.matcher("/api/sn/*/query/**"),
                                                                mvc.matcher("/api/sn/*/chat"),
                                                                mvc.matcher("/api/sn/*/chat/**"),
                                                                mvc.matcher("/api/sn/*/*/spell-check"),
                                                                mvc.matcher("/api/ann/*/search"),
                                                                mvc.matcher("/ann/**"),
                                                                mvc.matcher("/pages/**"),
                                                                // T245 — MCP server endpoint (loopback-gated, see CSRF note above).
                                                                mvc.matcher("/mcp"),
                                                                mvc.matcher("/mcp/**"),
                                                                mvc.matcher("/favicon.ico"),
                                                                mvc.matcher("/*.png"),
                                                                mvc.matcher("/manifest.json"),
                                                                mvc.matcher("/browserconfig.xml"),
                                                                mvc.matcher("/console/**"),
                                                                mvc.matcher("/api/v2/guest/**"),
                                                                mvc.matcher("/api/v2/user/register"),
                                                                mvc.matcher("/api/signup"),
                                                                mvc.matcher(LOGIN_PATH),
                                                                mvc.matcher(SETUP_PATH)).permitAll();
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
                return web -> web.httpFirewall(allowUrlEncodedSlaturHttpFirewall()).ignoring()
                                .requestMatchers(mvc.matcher("/h2/**"));
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
