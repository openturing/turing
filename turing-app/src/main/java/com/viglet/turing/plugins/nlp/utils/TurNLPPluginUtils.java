package com.viglet.turing.plugins.nlp.utils;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.http.MediaType;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TurNLPPluginUtils {

    public static void handleEntity(String entityType, String entity, Map<String, List<String>> entityList) {
        if (entityList.containsKey(entityType)) {
            if (!entityList.get(entityType).contains(entity) && entity.trim().length() > 1) {
                entityList.get(entityType).add(entity.trim());
            }
        } else {
            List<String> valueList = new ArrayList<>();
            valueList.add(entity.trim());
            entityList.put(entityType, valueList);
        }

    }

    @NotNull
    public static HttpPost getHttpPost(URL serverURL, JSONObject jsonBody) {
        HttpPost httpPost = new HttpPost(serverURL.toString());

        httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());
        StringEntity stringEntity = new StringEntity(jsonBody.toString(), StandardCharsets.UTF_8);
        httpPost.setEntity(stringEntity);
        return httpPost;
    }

}
