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
package com.viglet.turing;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import com.viglet.turing.console.TurConsole;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.web.filter.CharacterEncodingFilter;

@SpringBootApplication
@EnableJms
@EnableCaching
@EnableEncryptableProperties
public class TuringES {
	public static void main(String... args) {
		if (args != null && args.length > 0 && args[0].equals("console")) {
			new SpringApplicationBuilder(TurConsole.class).web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF)
					.run(args);
		} else {
			System.out.println(":: Starting Turing ES ...");
			SpringApplication.run(TuringES.class, args);
			System.out.println(":: Started Turing ES");
		}
	}
	@Bean
	FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean() {
		FilterRegistrationBean<CharacterEncodingFilter> registrationBean = new FilterRegistrationBean<>();
		CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
		characterEncodingFilter.setForceEncoding(true);
		characterEncodingFilter.setEncoding("UTF-8");
		registrationBean.setFilter(characterEncodingFilter);
		return registrationBean;
	}
	@Bean
	Module hibernate5Module() {
		return new Hibernate5JakartaModule();
	}

}