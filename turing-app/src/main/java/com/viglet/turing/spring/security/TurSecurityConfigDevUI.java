/*
 * Copyright (C) 2016-2020 Alexandre Oliveira <alexandre.oliveira@viglet.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as publitured by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You turould have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.spring.security;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
@EnableWebSecurity
@Profile("dev-ui")
@ComponentScan(basePackageClasses = TurCustomUserDetailsService.class)
public class TurSecurityConfigDevUI extends TurSecurityConfigProduction {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.headers().frameOptions().disable().cacheControl().disable();
        http.httpBasic().authenticationEntryPoint(turAuthenticationEntryPoint).and().authorizeRequests()
                .antMatchers("/index.html", "/welcome/**", "/", "/assets/**", "/swagger-resources/**", "/h2/**",
                        "/sn/**", "/fonts/**", "/api/sn/**", "/favicon.ico", "/*.png", "/manifest.json",
                        "/browserconfig.xml", "/console/**")
                .permitAll().anyRequest().authenticated().and()
                .addFilterAfter(new TurCsrfHeaderFilter(), CsrfFilter.class).csrf().ignoringAntMatchers("/api/sn/**")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).and().logout();
        http.csrf().disable();
        http.cors();
    }

}