package com.viglet.turing.spring.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.userdetails.DaoAuthenticationConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unchecked", "rawtypes" })
class TurSecurityConfigProductionTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManagerBuilder authenticationManagerBuilder;

    @Mock
    private DaoAuthenticationConfigurer daoAuthenticationConfigurer;

    private TurSecurityConfigProduction config;

    @BeforeEach
    void setUp() {
        config = new TurSecurityConfigProduction(userDetailsService, passwordEncoder);
    }

    @Test
    void shouldExposeErrorPathConstant() {
        assertThat(TurSecurityConfigProduction.ERROR_PATH).isEqualTo("/error/**");
    }

    @Test
    void shouldAllowUrlEncodedSlashInFirewall() {
        StrictHttpFirewall firewall = (StrictHttpFirewall) config.allowUrlEncodedSlaturHttpFirewall();

        assertThat(firewall).isInstanceOf(StrictHttpFirewall.class);
    }

    @Test
    void shouldConfigureAuthenticationManagerWithUserDetailsServiceAndPasswordEncoder() {
        when(authenticationManagerBuilder.userDetailsService(userDetailsService))
                .thenReturn(daoAuthenticationConfigurer);

        config.configureGlobal(authenticationManagerBuilder);

        verify(authenticationManagerBuilder).userDetailsService(userDetailsService);
        verify(daoAuthenticationConfigurer).passwordEncoder(passwordEncoder);
    }
}
