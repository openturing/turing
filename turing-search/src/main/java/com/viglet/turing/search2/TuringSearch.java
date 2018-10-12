/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.search2;

import java.io.File;


import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.filter.CharacterEncodingFilter;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

import io.undertow.UndertowOptions;

@SpringBootApplication
@EnableJms
@EnableCaching
public class TuringSearch {

	public static void main(String[] args) throws Exception {		
		System.out.println("Viglet Turing starting...");
		SpringApplication.run(TuringSearch.class, args);
		System.out.println("Viglet Turing started");
	}
	
	@Bean
	public FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean() {
		FilterRegistrationBean<CharacterEncodingFilter> registrationBean = new FilterRegistrationBean<CharacterEncodingFilter>();
		CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
		characterEncodingFilter.setForceEncoding(true);
		characterEncodingFilter.setEncoding("UTF-8");
		registrationBean.setFilter(characterEncodingFilter);
		return registrationBean;
	}

	@Bean
	public Module hibernate5Module() {
		return new Hibernate5Module();
	}

	@Bean
	UndertowServletWebServerFactory embeddedServletContainerFactory() {
		UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
		factory.addBuilderCustomizers(builder -> builder.setServerOption(UndertowOptions.ENABLE_HTTP2, true));
		return factory;
	}
	
	@Bean(initMethod = "start", destroyMethod = "stop")
	public BrokerService broker() throws Exception {
		final BrokerService broker = new BrokerService();
		// broker.addConnector("tcp://localhost:61616");
		broker.addConnector("vm://localhost");
		PersistenceAdapter persistenceAdapter = new KahaDBPersistenceAdapter();

		File userDir = new File(System.getProperty("user.dir"));
		File queueDir = null;
		if (userDir.exists() && userDir.isDirectory()) {
			queueDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "queue"));
			if (!queueDir.exists()) {
				queueDir.mkdirs();
			}

		} else {
			queueDir = new File(System.getProperty("user.home") + File.separator + "turing-queue");
			if (!queueDir.exists()) {
				queueDir.mkdirs();
			}
		}
		persistenceAdapter.setDirectory(queueDir);
		broker.setPersistenceAdapter(persistenceAdapter);
		broker.setPersistent(true);
		return broker;
	}

	@RequestMapping("/")
	String index() {
		return "index";
	}
}
