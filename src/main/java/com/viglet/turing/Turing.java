/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing;

import java.io.File;

import javax.servlet.MultipartConfigElement;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.CharacterEncodingFilter;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import com.viglet.turing.console.TurConsole;

@SpringBootApplication
@EnableJms
@EnableCaching
@EnableEncryptableProperties
public class Turing {

	public static void main(String... args) throws Exception {

		if (args != null && args.length > 0 && args[0].equals("console")) {
			new SpringApplicationBuilder(TurConsole.class).web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF)
					.run(args);
		} else {
			System.out.println("Viglet Turing starting...");
			SpringApplication.run(Turing.class, args);
			System.out.println("Viglet Turing started");
		}

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

	@Bean
	public MultipartConfigElement multipartConfigElement() {

		MultipartConfigFactory factory = new MultipartConfigFactory();
		factory.setMaxFileSize(DataSize.ofMegabytes(1024L));
		factory.setMaxRequestSize(DataSize.ofMegabytes(1024L));

		return factory.createMultipartConfig();

	}

}