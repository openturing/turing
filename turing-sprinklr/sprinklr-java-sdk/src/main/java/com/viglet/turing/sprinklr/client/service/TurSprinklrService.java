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

package com.viglet.turing.sprinklr.client.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;

@Slf4j
public class TurSprinklrService {
    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String ACCEPT = "Accept";
    private static final String AUTHORIZATION = "Authorization";
    private static final String KEY = "Key";
    private static final String BEARER = "Bearer";

    private TurSprinklrService() {
        throw new IllegalStateException("Sprinklr Service class");
    }

    /**
     * Sends a request and return a POJO from the json response.
     * @param turSprinklrAccessToken Token for using Sprinklr API
     * @param endpoint The endpoint of API
     * @return Return de JSON response as a POJO from clazz type
     */
    public static <R> R executeService(Class<R> clazz, TurSprinklrAccessToken turSprinklrAccessToken, String endpoint,
                                       RequestBody requestBody) {
        log.info("Post Request: {}", endpoint);
        // Creates a client to send a request
        return getResponse(clazz, getRequest(turSprinklrAccessToken, endpoint, requestBody));
    }

    private static Request getRequest(TurSprinklrAccessToken turSprinklrAccessToken, String endpoint,
                                      RequestBody requestBody) {
        Request request = new Request.Builder()
                .url(endpoint)
                .method(POST, requestBody)
                .addHeader(AUTHORIZATION, "%s %s".formatted(BEARER, turSprinklrAccessToken.getAccessToken()))
                .addHeader(KEY, turSprinklrAccessToken.getApiKey())
                .addHeader(CONTENT_TYPE, JSON.toString())
                .addHeader(ACCEPT, JSON.toString())
                .build();
        log.debug(request.toString());
        return request;
    }

    private static <R> R getResponse(Class<R> clazz, Request request) {
        String responseBody = null;
        try (Response response = new OkHttpClient().newBuilder().build().newCall(request).execute()) {
            responseBody = response.peekBody(500l).string();
            if (response.body() != null) {
                return new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readValue(response.body().string(), clazz);
            }
        } catch (JsonParseException e) {
            log.error("Error parsing the response", e);
            log.error("The body of the response is: {}", responseBody);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
