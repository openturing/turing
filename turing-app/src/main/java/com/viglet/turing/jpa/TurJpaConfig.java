package com.viglet.turing.jpa;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.viglet.turing.spring.security.TurAuditorAwareImpl;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class TurJpaConfig {


    @Bean
    AuditorAware<String> auditorAware() {
        return new TurAuditorAwareImpl();
    }
}