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
import org.springframework.context.annotation.Scope;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@Configuration
@EnableWebSecurity
@Profile("dev-ui")
@ComponentScan(basePackageClasses = TurCustomUserDetailsService.class)
public class TurSecurityConfigDevUI extends TurSecurityConfigProduction {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc) throws Exception {
		http.headers(header -> header.frameOptions(
				frameOptions -> frameOptions.disable().cacheControl(cacheControl -> cacheControl.disable())));
		http.httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(turAuthenticationEntryPoint))
				.authorizeHttpRequests(authorizeRequests -> {
					authorizeRequests.requestMatchers(mvc.pattern("/index.html"), mvc.pattern("/welcome/**"),
							mvc.pattern("/"), mvc.pattern("/assets/**"), mvc.pattern("/swagger-resources/**"),
							mvc.pattern("/sn/**"), mvc.pattern("/fonts/**"), mvc.pattern("/api/sn/**"),
							mvc.pattern("/favicon.ico"), mvc.pattern("/*.png"), mvc.pattern("/manifest.json"),
							mvc.pattern("/browserconfig.xml"), mvc.pattern("/console/**"),
							mvc.pattern("/api/v2/guest/**")).permitAll();
					authorizeRequests.anyRequest().authenticated();
				}).csrf(csrf -> csrf.disable()).cors(cors -> cors.disable());
		return http.build();
	}

	@Scope("prototype")
	@Bean
	MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
		return new MvcRequestMatcher.Builder(introspector);
	}

}