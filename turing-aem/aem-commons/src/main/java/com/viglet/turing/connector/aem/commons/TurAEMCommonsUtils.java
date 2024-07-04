package com.viglet.turing.connector.aem.commons;

import com.google.common.net.UrlEscapers;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Slf4j
public class TurAEMCommonsUtils {
    private TurAEMCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    protected static final Map<String, String> responseHttpCache = new HashMap<>();

    public static Locale getLocaleByPath(TurAemSourceContext turAemSourceContext, String path) {
        for (TurAemLocalePathContext turAemSourceLocalePath : turAemSourceContext.getLocalePaths()) {
            if (hasPath(turAemSourceLocalePath, path)) {
                return turAemSourceLocalePath.getLocale();
            }
        }
        return turAemSourceContext.getDefaultLocale();
    }

    private static boolean hasPath(TurAemLocalePathContext turAemSourceLocalePath, String path) {
        return path.startsWith(turAemSourceLocalePath.getPath());
    }

    public static Locale getLocaleFromAemObject(TurAemSourceContext turAemSourceContext,
                                                AemObject aemObject) {
        return getLocaleByPath(turAemSourceContext, aemObject.getPath());
    }

    public static JSONObject getInfinityJson(String url, TurAemSourceContext turAemSourceContext) {
        return getInfinityJson(url, turAemSourceContext.getUrl(), turAemSourceContext.getUsername(), turAemSourceContext.getPassword());
    }

    public static JSONObject getInfinityJson(String originalUrl, String hostAndPort, String username, String password) {
        String infinityJsonUrl = String.format(originalUrl.endsWith(TurAEMAttrProcess.JSON) ? "%s%s" : "%s%s.infinity.json",
                hostAndPort, originalUrl);
        if (responseHttpCache.containsKey(infinityJsonUrl)) {
            log.info("Cached Response {}", infinityJsonUrl);
            return new JSONObject(responseHttpCache.get(infinityJsonUrl));
        } else {
            log.info("Request {}", infinityJsonUrl);
            return TurAEMCommonsUtils.getResponseBody(infinityJsonUrl, username, password).map(responseBody -> {
                if (TurAEMCommonsUtils.isResponseBodyJSONArray(responseBody) && !originalUrl.endsWith(TurAEMAttrProcess.JSON)) {
                    JSONArray jsonArray = new JSONArray(responseBody);
                    return getInfinityJson(jsonArray.getString(0), hostAndPort, username, password);
                } else if (TurAEMCommonsUtils.isResponseBodyJSONObject(responseBody)) {
                    responseHttpCache.put(infinityJsonUrl, responseBody);
                    return new JSONObject(responseBody);
                }
                return new JSONObject();
            }).orElse(new JSONObject());
        }
    }

    public static boolean hasProperty(JSONObject jsonObject, String property) {
        return jsonObject.has(property) && jsonObject.get(property) != null;
    }

    public static String getPropertyValue(Object property) {
        try {
            if (property instanceof JSONArray propertyArray) {
                return !propertyArray.isEmpty() ? propertyArray.get(0).toString() : "";
            } else if (property != null) {
                return property.toString();
            }
        } catch (IllegalStateException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static boolean isResponseBodyJSONArray(String responseBody) {
        return responseBody.startsWith("[");
    }

    public static boolean isResponseBodyJSONObject(String responseBody) {
        return responseBody.startsWith("{");
    }

    public static Optional<String> getResponseBody(String url, String username, String password) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.AUTHORIZATION, basicAuth(username, password))))
                .build()) {
            HttpGet request = new HttpGet(URI.create(UrlEscapers.urlFragmentEscaper().escape(url)).normalize());
            return httpClient.execute(request, response -> {
                HttpEntity entity = response.getEntity();
                return entity != null ? Optional.of(EntityUtils.toString(entity)) : Optional.empty();
            });
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    public static void getJsonNodeToComponent(JSONObject jsonObject, StringBuilder components) {
        if (jsonObject.has(TurAEMAttrProcess.JCR_TITLE) && jsonObject.get(TurAEMAttrProcess.JCR_TITLE)
                instanceof String title) {
            components.append(title);
        } else if (jsonObject.has(TurAEMAttrProcess.TEXT) && jsonObject.get(TurAEMAttrProcess.TEXT)
                instanceof String text) {
            components.append(text);
        }
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(TurAEMAttrProcess.JCR) && !key.startsWith(TurAEMAttrProcess.SLING)
                    && (jsonObject.get(key) instanceof JSONObject jsonObjectNode)) {
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }

    public static Locale getLocaleFromContext(TurAemSourceContext turAemSourceContext, TurCmsContext context) {
        AemObject aemObject = (AemObject) context.getCmsObjectInstance();
        return getLocaleFromAemObject(turAemSourceContext, aemObject);
    }

    public static void cleanCache() {
        responseHttpCache.clear();
    }
}
