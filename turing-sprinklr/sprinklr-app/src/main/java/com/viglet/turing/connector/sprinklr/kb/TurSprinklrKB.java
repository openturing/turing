package com.viglet.turing.connector.sprinklr.kb;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.connector.sprinklr.commons.bean.TurSprinklrSearch;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TurSprinklrKB {
    public static final MediaType JSON = MediaType.get("application/json");
    public static final String KB_SERVICE = "https://api2.sprinklr.com/%s/api/v2/knowledgebase/search";

    public TurSprinklrSearch run(TurSprinklrSource turSprinklrSource){
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
                .url(KB_SERVICE.formatted(turSprinklrSource.getEnvironment()))
                .method("POST", body)
                .addHeader("Authorization", "Bearer " + turSprinklrSource.getAuthorizationCode())
                .addHeader("Key", turSprinklrSource.getApiKey())
                .addHeader("Content-Type", JSON.toString())
                .addHeader("Accept", JSON.toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body() != null ?
                    new ObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(response.body().string(), TurSprinklrSearch.class) : null;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
