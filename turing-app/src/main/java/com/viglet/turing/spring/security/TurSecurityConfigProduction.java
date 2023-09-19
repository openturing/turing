/*
 * Copyright (C) 2016-2022 the original author or authors.
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Profile("production")
@ComponentScan(basePackageClasses = TurCustomUserDetailsService.class)
public class TurSecurityConfigProduction {
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    protected TurAuthenticationEntryPoint turAuthenticationEntryPoint;
    @Autowired
    private TurConfigProperties turConfigProperties;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc) throws Exception {
        http.headers(header -> header.frameOptions(
                frameOptions -> frameOptions.disable().cacheControl(HeadersConfigurer.CacheControlConfig::disable)));
        if (turConfigProperties.isKeycloak()) {
            http.oauth2Login(withDefaults());
            http.authorizeHttpRequests(authorizeRequests -> authorizeRequests.anyRequest().authenticated());
            http.csrf(AbstractHttpConfigurer::disable);
            http.cors(Customizer.withDefaults());
            http.logout(logout ->
                    logout.logoutSuccessUrl("http://172.28.32.1:8080/realms/demo/protocol/openid-connect/logout?post_logout_redirect_uri=http://172.28.32.1:2700/&client_id=demo-app"));

        } else {
            http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(turAuthenticationEntryPoint));
            http.authorizeHttpRequests(authorizeRequests -> {
                authorizeRequests.requestMatchers(mvc.pattern("/index.html"), mvc.pattern("/welcome/**"),
                        mvc.pattern("/"), mvc.pattern("/assets/**"), mvc.pattern("/swagger-resources/**"),
                        mvc.pattern("/sn/**"), mvc.pattern("/fonts/**"), mvc.pattern("/api/sn/**"),
                        mvc.pattern("/favicon.ico"), mvc.pattern("/*.png"), mvc.pattern("/manifest.json"),
                        mvc.pattern("/browserconfig.xml"), mvc.pattern("/console/**"), mvc.pattern("/cloud/**"),
                        mvc.pattern("/admin/**"), mvc.pattern("/api/v2/guest/**"),
                        AntPathRequestMatcher.antMatcher("/h2/**")).permitAll();
                authorizeRequests.anyRequest().authenticated();
            });
            http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));


        }
        return http.build();
    }

    @Bean
    WebSecurityCustomizer webSecurityCustomizer(MvcRequestMatcher.Builder mvc) {
        return (web) -> {
            web.httpFirewall(allowUrlEncodedSlaturHttpFirewall()).ignoring().requestMatchers(mvc.pattern("/h2/**"));
        };
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
}