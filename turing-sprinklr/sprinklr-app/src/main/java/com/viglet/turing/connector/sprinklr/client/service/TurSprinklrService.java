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

package com.viglet.turing.connector.sprinklr.client.service;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;


@Slf4j
@Component
public class TurSprinklrService {
    public static final String SPRINKLR = "sprinklr";
    private final RestClient restClient;

    @Inject
    private TurSprinklrService(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Sends a request and return a POJO from the json response.
     * @param endpoint The endpoint of API
     * @return Return de JSON response as a POJO from clazz type
     */
    public <R> R executeService(Class<R> clazz, String endpoint,
                                       String requestBody) {
        log.info("Post Request: {}", endpoint);
        return restClient.post()
                .uri(endpoint)
                .attributes(clientRegistrationId(SPRINKLR))
                .contentType(APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(clazz);
    }
}
