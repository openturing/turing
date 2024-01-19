package com.viglet.turing.connector.aem.indexer;

import com.google.common.net.UrlEscapers;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
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
import java.util.Locale;

@Slf4j
public class TurAemUtils {

    public static final String JCR_TITLE = "jcr:title";
    public static final String TEXT = "text";
    public static final String JCR = "jcr:";
    public static final String JSON = ".json";
    public static final String SLING = "sling:";

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

    public static JSONObject getInfinityJson(String url, TurAEMIndexerTool tool) {

            return getInfinityJson(url, tool.getHostAndPort(),tool.getUsername(),tool.getPassword());
    }
    public static JSONObject getInfinityJson(String url, String hostAndPort, String username, String password) {
        String infinityJsonUrl = String.format(url.endsWith(JSON) ? "%s%s" : "%s%s.infinity.json",
                hostAndPort, url);
        log.info(STR."Request \{infinityJsonUrl}");
        String responseBody = getResponseBody(infinityJsonUrl, username, password);
        if (isResponseBodyJSONArray(responseBody) && !url.endsWith(JSON)) {
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
        try(HttpClient client = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                })
                .build())
        {
            try {
                HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(UrlEscapers.urlFragmentEscaper().escape(url))).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return response.body();
            } catch (URISyntaxException | IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }
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
        jsonObject.toMap().forEach((key, _) -> {
            if (jsonObject.has(JCR_TITLE))
                components.append(jsonObject.getString(JCR_TITLE));
            if (jsonObject.has(TEXT))
                components.append(jsonObject.getString(TEXT));
            if (!key.startsWith(JCR) && !key.startsWith(SLING) && (jsonObject.get(key) instanceof JSONObject)) {
                JSONObject jsonObjectNode = jsonObject.getJSONObject(key);
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }
}
