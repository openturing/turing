package com.viglet.turing.connector.aem.indexer;

import com.google.common.net.UrlEscapers;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class TurAemUtils {
    public static boolean hasProperty(JSONObject jsonObject, String property) {
        return jsonObject.has(property) && jsonObject.get(property) != null;
    }

    public static JSONObject getInfinityJson(String url, String hostAndPort, String username, String password) {
        String  responseBody = getResponseBody(String.format(url.endsWith(".json")? "%s%s": "%s%s.infinity.json",
                    hostAndPort, url), username, password);
        if (isResponseBodyJSONArray(responseBody) && !url.endsWith(".json")) {
            JSONArray jsonArray = new JSONArray(responseBody);
            return getInfinityJson(jsonArray.getString(0), hostAndPort, username, password);
        } else if (isResponseBodyJSONObject(responseBody)) {
            return new JSONObject(responseBody);
        }
        return new JSONObject();
    }

    private static boolean isResponseBodyJSONArray(String responseBody) {
        return responseBody.startsWith("[");
    }
    private static boolean isResponseBodyJSONObject(String responseBody) {
        return responseBody.startsWith("{");
    }

    public static String getResponseBody(String url, String username, String password) {
        HttpClient client = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                })
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(UrlEscapers.urlFragmentEscaper().escape(url))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String getPropertyValue(Object property) {
        try {
            if (property instanceof JSONArray propertyArray) {
                return !propertyArray.isEmpty() ? propertyArray.get(0).toString() : "";
            } else
                return property.toString();
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static void getJsonNodeToComponent(JSONObject jsonObject, StringBuffer components) {
        jsonObject.toMap().forEach((key, value) -> {
            if (jsonObject.has("jcr:title"))
                components.append(jsonObject.getString("jcr:title"));
            if (jsonObject.has("text"))
                components.append(jsonObject.getString("text"));
            if (!key.startsWith("jcr:")) {
                JSONObject jsonObjectNode = jsonObject.getJSONObject(key);
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }
}
