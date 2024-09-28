package com.viglet.turing.sprinklr.client.service;

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
     * @author Alexandre
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
        try (Response response = new OkHttpClient().newBuilder().build().newCall(request).execute()) {
            if (response.body() != null) {
                return new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readValue(response.body().string(), clazz);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
