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

package com.viglet.turing.spring;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;

import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@AutoConfigureAfter(DispatcherServletAutoConfiguration.class)
public class TurStaticResourceConfiguration implements WebMvcConfigurer {
	@Value("${turing.allowedOrigins:localhost}")
	private String allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("PUT", "DELETE", "GET", "POST")
				.allowCredentials(false).maxAge(3600);
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.setUseTrailingSlashMatch(true);
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/console").setViewName("forward:/console/index.html");
		registry.addViewController("/console/").setViewName("forward:/console/index.html");
		registry.addViewController("/cloud").setViewName("forward:/cloud/index.html");
		registry.addViewController("/cloud/").setViewName("forward:/cloud/index.html");
		registry.addViewController("/welcome").setViewName("forward:/welcome/index.html");
		registry.addViewController("/welcome/").setViewName("forward:/welcome/index.html");
		registry.addViewController("/converse").setViewName("forward:/converse/index.html");
		registry.addViewController("/converse/").setViewName("forward:/converse/index.html");

	}

	@Override
	public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
		configurer.setDefaultTimeout(-1);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/console/**").addResourceLocations("classpath:/public/console/")
				.resourceChain(true).addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						Resource requestedResource = location.createRelative(resourcePath);

						return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
								: new ClassPathResource("/public/console/index.html");
					}
				});
		registry.addResourceHandler("/cloud/**").addResourceLocations("classpath:/public/cloud/").resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						Resource requestedResource = location.createRelative(resourcePath);

						return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
								: new ClassPathResource("/public/cloud/index.html");
					}
				});
		registry.addResourceHandler("/welcome/**").addResourceLocations("classpath:/public/welcome/")
				.resourceChain(true).addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						Resource requestedResource = location.createRelative(resourcePath);

						return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
								: new ClassPathResource("/public/welcome/index.html");
					}
				});
	}
}