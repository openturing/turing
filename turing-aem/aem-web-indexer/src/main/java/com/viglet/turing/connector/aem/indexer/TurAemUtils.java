package com.viglet.turing.connector.aem.indexer;

import com.google.common.net.UrlEscapers;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Slf4j
public class TurAemUtils {

    public static final String JCR_TITLE = "jcr:title";
    public static final String TEXT = "text";
    public static final String JCR = "jcr:";
    public static final String JSON = ".json";
    public static final String SLING = "sling:";

    public static final Map<String, String> responseHttpCache = new HashMap<>();

    public static Locale getLocaleFromAemObject(AemHandlerConfiguration config, AemObject aemObject) {
        return LocaleUtils.toLocale(config.getLocaleByPath(config.getDefaultSNSiteConfig().getName(),
                aemObject.getPath()));
    }

    public static Locale getLocaleFromContext(TurCmsContext context) {
        AemHandlerConfiguration config = (AemHandlerConfiguration) context.getConfiguration();
        AemObject aemObject = (AemObject) context.getCmsObjectInstance();
        return getLocaleFromAemObject(config, aemObject);
    }

    public static boolean hasProperty(JSONObject jsonObject, String property) {
        return jsonObject.has(property) && jsonObject.get(property) != null;
    }

    public static JSONObject getInfinityJson(String url, TurAemContext context) {
        String infinityJsonUrl = String.format(url.endsWith(JSON) ? "%s%s" : "%s%s.infinity.json",
                context.getUrl(), url);
        if (responseHttpCache.containsKey(infinityJsonUrl)) {
            log.info("Cached Response {}", infinityJsonUrl);
            return new JSONObject(responseHttpCache.get(infinityJsonUrl));
        } else {
            log.info("Request {}", infinityJsonUrl);
            return getResponseBody(infinityJsonUrl, context.getUsername(), context.getPassword()).map(responseBody -> {
                if (isResponseBodyJSONArray(responseBody) && !url.endsWith(JSON)) {
                    return getInfinityJson(new JSONArray(responseBody).getString(0), context);
                } else if (isResponseBodyJSONObject(responseBody)) {
                    responseHttpCache.put(infinityJsonUrl, responseBody);
                    return new JSONObject(responseBody);
                }
                return new JSONObject();
            }).orElse(new JSONObject());
        }
    }

    private static boolean isResponseBodyJSONArray(String responseBody) {
        return responseBody.startsWith("[");
    }

    private static boolean isResponseBodyJSONObject(String responseBody) {
        return responseBody.startsWith("{");
    }

    public static Optional<String> getResponseBody(String url, String username, String password) {
        HttpGet request = new HttpGet(URI.create(UrlEscapers.urlFragmentEscaper().escape(url)).normalize());
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.AUTHORIZATION, basicAuth(username, password))))
                .build();
             CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return Optional.of(EntityUtils.toString(entity));
            }
        } catch (IOException | ParseException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
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

    public static void getJsonNodeToComponent(JSONObject jsonObject, StringBuffer components) {
        if (jsonObject.has(JCR_TITLE) && jsonObject.get(JCR_TITLE) instanceof String title) {
            components.append(title);
        } else if (jsonObject.has(TEXT) && jsonObject.get(TEXT) instanceof String text) {
            components.append(text);
        }
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(SLING)
                    && (jsonObject.get(key) instanceof JSONObject jsonObjectNode)) {
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }
}
