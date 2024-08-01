package com.viglet.turing.connector.sprinklr.kb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.bean.TurSprinklrSearch;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TurSprinklrKB {
    public static final MediaType JSON = MediaType.get("application/json");
    public static final String KB_SERVICE = "https://api2.sprinklr.com/%s/api/v2/knowledgebase/search";
    private final String environment;
    private final String authorization;
    private final String apiKey;

    @Inject
    public TurSprinklrKB(@Value("${turing.sprinklr.environment}") String environment,
                         @Value("${turing.sprinklr.authorization.code}") String authorization,
                         @Value("${turing.sprinklr.apiKey}") String apiKey) {
        this.environment = environment;
        this.authorization = authorization;
        this.apiKey = apiKey;
    }

    public TurSprinklrSearch run() throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        RequestBody body = RequestBody.create("""
                {
                    "filters": [],
                    "page": {
                        "page": 0,
                        "size": 500
                    }
                }
                """, JSON);
        Request request = new Request.Builder()
                .url(KB_SERVICE.formatted(environment))
                .method("POST", body)
                .addHeader("Authorization", "Bearer " + authorization)
                .addHeader("Key", apiKey)
                .addHeader("Content-Type", JSON.toString())
                .addHeader("Accept", JSON.toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body() != null ?
                    new ObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(response.body().string(), TurSprinklrSearch.class) : null;
        }
    }
}
