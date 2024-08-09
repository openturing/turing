package com.viglet.turing.connector.sprinklr.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrToken;
import com.viglet.turing.connector.sprinklr.service.token.TurSprinklrTokenService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TurSprinklrService {
    private final TurSprinklrTokenService turSprinklrTokenService;
    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String ACCEPT = "Accept";
    private static final String AUTHORIZATION = "Authorization";
    private static final String KEY = "Key";
    private static final String BEARER = "Bearer";

    @Inject
    public TurSprinklrService(TurSprinklrTokenService turSprinklrTokenService) {
        this.turSprinklrTokenService = turSprinklrTokenService;
    }

    public <R> R  executeService(Class<R> clazz, TurSprinklrSource turSprinklrSource, String endpoint,
                                RequestBody requestBody) {
        TurSprinklrToken turSprinklrToken = turSprinklrTokenService.getAccessToken(turSprinklrSource);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        try {
            Request request = new Request.Builder()
                    .url(endpoint)
                    .method(POST, requestBody)
                    .addHeader(AUTHORIZATION, "%s %s".formatted(BEARER, turSprinklrToken.getAccessToken()))
                    .addHeader(KEY, turSprinklrToken.getApiKey())
                    .addHeader(CONTENT_TYPE, JSON.toString())
                    .addHeader(ACCEPT, JSON.toString())
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.body() != null ?
                        new ObjectMapper()
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                .readValue(response.body().string(), clazz) : null;
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
