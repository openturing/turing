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
//import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
//import org.springframework.security.oauth2.config.annotation.web.configuration.EnableOAuth2Client;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.bind.annotation.RestController;

@Configuration
@EnableWebSecurity
@Profile("production")
public class TurSecurityConfig extends WebSecurityConfigurerAdapter {
	   
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.headers().frameOptions().disable().cacheControl().disable();
		http.csrf().disable();
		http.cors().disable();
	}

	@Override
	public void configure(WebSecurity web) throws Exception {

		super.configure(web);
		web.ignoring().antMatchers("/webjars/**", "/js/**", "/css/**", "/template/**", "/img/**", "/sites/**",
				"/swagger-resources/**", "/h2/**");
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

/*
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
*/