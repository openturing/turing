package com.viglet.turing;

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
