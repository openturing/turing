package com.viglet.turing.connector.sprinklr;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrKeyValueTransformer;
import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.filter.CharacterEncodingFilter;

@SpringBootApplication
@EnableScheduling
public class TurSprinklrApplication {
    public static final String UTF_8 = "UTF-8";

    public static void main(String[] args) {
        SpringApplication.run(TurSprinklrApplication.class, args);
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

    @Bean
    public TurSprinklrPluginContext pluginContext(TurSprinklrKeyValueTransformer transformer) {
        TurSprinklrPluginContext pluginContext = new TurSprinklrPluginContext();
        pluginContext.addPlugin(transformer);
        return pluginContext;
    }

    @Bean
    public TurSprinklrKeyValueTransformer keyValueTransformer() {
        return new TurSprinklrKeyValueTransformer();
    }
}
