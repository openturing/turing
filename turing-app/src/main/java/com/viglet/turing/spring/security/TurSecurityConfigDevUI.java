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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
@EnableWebSecurity
@Profile("dev-ui")
@ComponentScan(basePackageClasses = TurCustomUserDetailsService.class)
public class TurSecurityConfigDevUI extends TurSecurityConfigProduction {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.headers().frameOptions().disable().cacheControl().disable();
		http.httpBasic().authenticationEntryPoint(turAuthenticationEntryPoint).and().authorizeRequests()
				.antMatchers("/index.html", "/welcome/**", "/", "/assets/**", "/swagger-resources/**", "/h2/**",
						"/sn/**", "/fonts/**", "/api/sn/**", "/favicon.ico", "/*.png", "/manifest.json",
						"/browserconfig.xml", "/console/**")
				.permitAll().anyRequest().authenticated().and()
				.addFilterAfter(new TurCsrfHeaderFilter(), CsrfFilter.class).csrf().ignoringAntMatchers("/api/sn/**")
				.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).and().logout();
		http.csrf().disable();
		http.cors();
		return http.build();
	}

}