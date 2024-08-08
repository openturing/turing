package com.viglet.turing.connector.sprinklr.kb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.commons.bean.TurSprinklrSearch;
import com.viglet.turing.connector.sprinklr.kb.request.TurSprinklrKBPage;
import com.viglet.turing.connector.sprinklr.kb.request.TurSprinklrKBRequestBody;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrToken;
import com.viglet.turing.connector.sprinklr.token.TurSprinklrTokenService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TurSprinklrKBService {
    public static final MediaType JSON = MediaType.get("application/json");
    public static final String KB_SERVICE = "https://api2.sprinklr.com/%s/api/v2/knowledgebase/search";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String POST = "POST";
    public static final String ACCEPT = "Accept";
    public static final String AUTHORIZATION = "Authorization";
    public static final String KEY = "Key";
    public static final String BEARER = "Bearer";
    private final TurSprinklrTokenService turSprinklrTokenService;

    @Inject
    public TurSprinklrKBService(TurSprinklrTokenService turSprinklrTokenService) {
        this.turSprinklrTokenService = turSprinklrTokenService;
    }

    public TurSprinklrSearch run(TurSprinklrSource turSprinklrSource, int page) {
        TurSprinklrToken turSprinklrToken = turSprinklrTokenService.getAccessToken(turSprinklrSource);
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        try {
            Request request = new Request.Builder()
                    .url(KB_SERVICE.formatted(turSprinklrSource.getEnvironment()))
                    .method(POST, RequestBody.create(new ObjectMapper().writeValueAsString(getRequestBody(page)), JSON))
                    .addHeader(AUTHORIZATION, "%s %s".formatted(BEARER, turSprinklrToken.getAccessToken()))
                    .addHeader(KEY, turSprinklrSource.getApiKey())
                    .addHeader(CONTENT_TYPE, JSON.toString())
                    .addHeader(ACCEPT, JSON.toString())
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.body() != null ?
                        new ObjectMapper()
                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                .readValue(response.body().string(), TurSprinklrSearch.class) : null;
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static TurSprinklrKBRequestBody getRequestBody(int page) {
        return TurSprinklrKBRequestBody.builder()
                .page(getPage(page)).build();
    }

    private static TurSprinklrKBPage getPage(int page) {
        return TurSprinklrKBPage.builder()
                .size(50)
                .page(page).build();
    }


}
