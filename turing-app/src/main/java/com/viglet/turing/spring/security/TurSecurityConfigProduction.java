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

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.spring.security.auth.TurAuthTokenHeaderFilter;
import com.viglet.turing.spring.security.auth.TurLogoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Profile("production")
@EnableMethodSecurity(securedEnabled = true)
@ComponentScan(basePackageClasses = TurCustomUserDetailsService.class)
public class TurSecurityConfigProduction {
    @Autowired
    private UserDetailsService userDetailsService;
    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri:''}")
    private String issuerUri;
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id:''}")
    private String clientId;
    @Value("${turing.url:'http://localhost:2700'}")
    private String turingUrl;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc,
                                    TurAuthTokenHeaderFilter turAuthTokenHeaderFilter,
                                    TurLogoutHandler turLogoutHandler,
                                    TurConfigProperties turConfigProperties,
                                    TurAuthenticationEntryPoint turAuthenticationEntryPoint) throws Exception {
        http.headers(header -> header.frameOptions(
                frameOptions -> frameOptions.disable().cacheControl(HeadersConfigurer.CacheControlConfig::disable)));
        http.cors(Customizer.withDefaults());
        http.addFilterBefore(turAuthTokenHeaderFilter, BasicAuthenticationFilter.class);
        http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new TurSpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(
                                mvc.pattern("/api/sn/**"),
                                mvc.pattern("/error/**"),
                                mvc.pattern("/logout"),
                                mvc.pattern("/api/nlp/**"),
                                mvc.pattern("/api/v2/guest/**"),
                                AntPathRequestMatcher.antMatcher("/h2/**")))
                .addFilterAfter(new TurCsrfCookieFilter(), BasicAuthenticationFilter.class);
        if (turConfigProperties.isKeycloak()) {
            String keycloakUrlFormat =
                    String.format("%s/protocol/openid-connect/logout?client_id=%s&post_logout_redirect_uri=%s",
                            issuerUri, clientId, turingUrl);
            http.oauth2Login(withDefaults());
            http.authorizeHttpRequests(authorizeRequests -> {
                authorizeRequests.requestMatchers(
                        mvc.pattern("/error/**"),
                        mvc.pattern("/api/discovery"),
                        mvc.pattern("/assets/**"),
                        mvc.pattern("/favicon.ico"),
                        mvc.pattern("/*.png"),
                        mvc.pattern("/manifest.json"),
                        mvc.pattern("/swagger-resources/**"),
                        mvc.pattern("/browserconfig.xml"),
                        AntPathRequestMatcher.antMatcher("/api/sn/**/ac"),
                        AntPathRequestMatcher.antMatcher("/api/sn/**/search"),
                        AntPathRequestMatcher.antMatcher("/api/sn/**/search/**"),
                        AntPathRequestMatcher.antMatcher("/api/sn/**/**/spell-check")).permitAll();
                authorizeRequests.anyRequest().authenticated();
            });
            http.logout(logout -> logout.addLogoutHandler(turLogoutHandler)
                    .logoutSuccessUrl(keycloakUrlFormat));
        } else {
            http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(turAuthenticationEntryPoint))
                    .authorizeHttpRequests(authorizeRequests -> {
                        authorizeRequests.requestMatchers(
                                mvc.pattern("/error/**"),
                                mvc.pattern("/api/discovery"),
                                mvc.pattern("/logout"),
                                mvc.pattern("/index.html"),
                                mvc.pattern("/welcome/**"),
                                mvc.pattern("/"),
                                AntPathRequestMatcher.antMatcher("/assets/**"),
                                mvc.pattern("/swagger-resources/**"),
                                mvc.pattern("/sn/**"),
                                mvc.pattern("/fonts/**"),
                                AntPathRequestMatcher.antMatcher("/api/sn/**/ac"),
                                AntPathRequestMatcher.antMatcher("/api/sn/**/search"),
                                AntPathRequestMatcher.antMatcher("/api/sn/**/search/**"),
                                AntPathRequestMatcher.antMatcher("/api/sn/**/**/spell-check"),
                                AntPathRequestMatcher.antMatcher("/favicon.ico"),
                                AntPathRequestMatcher.antMatcher("/*.png"),
                                AntPathRequestMatcher.antMatcher("/manifest.json"),
                                mvc.pattern("/browserconfig.xml"), mvc.pattern("/console/**"),
                                mvc.pattern("/api/v2/guest/**")).permitAll();
                        authorizeRequests.anyRequest().authenticated();

                    });
            http.logout(logout -> logout.addLogoutHandler(turLogoutHandler).logoutSuccessUrl("/"));
        }
        return http.build();
    }

    @Bean
    WebSecurityCustomizer webSecurityCustomizer(MvcRequestMatcher.Builder mvc) {
        return (web) ->
            web.httpFirewall(allowUrlEncodedSlaturHttpFirewall()).ignoring().requestMatchers(mvc.pattern("/h2/**"));
    }

    @Scope("prototype")
    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector handlerMappingIntrospector) {
        return new MvcRequestMatcher.Builder(handlerMappingIntrospector);
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService);
    }

    @Bean(name = "passwordEncoder")
    PasswordEncoder passwordencoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpFirewall allowUrlEncodedSlaturHttpFirewall() {
        // Allow double slash in URL
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }

    @Bean
    public RoleHierarchy roleHierarchy() {
        RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
        String hierarchy = "ROLE_ADMIN > ROLE_STAFF \n ROLE_STAFF > ROLE_USER";
        roleHierarchy.setHierarchy(hierarchy);
        return roleHierarchy;
    }
    @Bean
    public DefaultWebSecurityExpressionHandler customWebSecurityExpressionHandler() {
        DefaultWebSecurityExpressionHandler expressionHandler = new DefaultWebSecurityExpressionHandler();
        expressionHandler.setRoleHierarchy(roleHierarchy());
        return expressionHandler;
    }

}