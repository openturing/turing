package com.viglet.turing.connector.webcrawler;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.CharacterEncodingFilter;

@SpringBootApplication
public class TurWCApplication {
    public static final String UTF_8 = "UTF-8";

    public static void main(String[] args) {
        SpringApplication.run(TurWCApplication.class, args);
    }

    @Bean
    FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean() {
        FilterRegistrationBean<CharacterEncodingFilter> registrationBean = new FilterRegistrationBean<>();
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
        characterEncodingFilter.setForceEncoding(true);
        characterEncodingFilter.setEncoding(UTF_8);
        registrationBean.setFilter(characterEncodingFilter);
        return registrationBean;
    }
    @Bean
    Module hibernate5Module() {
        return new Hibernate5JakartaModule();
    }
}
