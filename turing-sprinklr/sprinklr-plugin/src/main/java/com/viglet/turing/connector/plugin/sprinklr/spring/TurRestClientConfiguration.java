/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.plugin.sprinklr.spring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.util.Collections;

import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.CLIENT_ID;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.CLIENT_SECRET;

@Configuration
public class TurRestClientConfiguration {
    @Value("${spring.security.oauth2.client.registration.sprinklr.client-id}")
    private String clientId;
    @Value("${spring.security.oauth2.client.registration.sprinklr.client-secret}")
    private String clientSecret;
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clients) {
        RestClientClientCredentialsTokenResponseClient accessTokenResponseClient =
                new RestClientClientCredentialsTokenResponseClient();
        accessTokenResponseClient.setParametersCustomizer(parameters -> {
            parameters.put(CLIENT_ID, Collections.singletonList(clientId));
            parameters.put(CLIENT_SECRET, Collections.singletonList(clientSecret));

        });
        OAuth2AuthorizedClientService service =
                new InMemoryOAuth2AuthorizedClientService(clients);
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clients, service);
        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials(clientCredentialsGrantBuilder ->
                                clientCredentialsGrantBuilder.accessTokenResponseClient(accessTokenResponseClient))
                        .build();
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager) {
        OAuth2ClientHttpRequestInterceptor requestInterceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        return builder
                .requestInterceptor(requestInterceptor).build();
    }

}