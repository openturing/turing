package com.viglet.turing.spring.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.bind.annotation.RestController;

@Configuration
@EnableWebSecurity
@RestController
@EnableOAuth2Client
@EnableAuthorizationServer
@Profile("production")
public class TurSecurityConfig extends WebSecurityConfigurerAdapter {

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		// Prevent the HTTP response header of "Pragma: no-cache".
		http.headers().frameOptions().disable().cacheControl().disable();
		http.authorizeRequests()
				.antMatchers("/index.html", "/welcome/**", "/", "/js/**", "/css/**", "/templates/**",
						"/img/**", "/swagger-resources/**", "/h2/**", "/webjars/**", "/console/**", "/fonts/**",  "/images/**", "/favicon.ico")
				.permitAll().anyRequest().authenticated().and().formLogin().permitAll();

	}

	@Override
	public void configure(WebSecurity web) throws Exception {
		// TODO Auto-generated method stub
		web.ignoring().antMatchers("/h2/**", "/welcome/**", "/webjars/**", "/js/**", "/index.html", "/css/**",
				"/img/**", "/", "/console/**", "/fonts/**", "/templates/**", "/images/**", "/favicon.ico");
		super.configure(web);
		web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
	}

	// create two users, admin and user
	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {

		auth.inMemoryAuthentication().withUser("user").password("{noop}user").roles("USER").and().withUser("admin")
				.password("{noop}admin").roles("ADMIN");

	}

	@Bean
	public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
		// Allow double slash in URL
		StrictHttpFirewall firewall = new StrictHttpFirewall();
		firewall.setAllowUrlEncodedSlash(true);
		return firewall;
	}
}