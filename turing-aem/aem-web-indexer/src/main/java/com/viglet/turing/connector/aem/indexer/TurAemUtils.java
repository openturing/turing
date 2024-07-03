package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.aem.commons.*;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

@Slf4j
public class TurAemUtils {

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
    public static Locale getLocaleFromAemObject(TurAemSourceContext turAemSource,
                                                AemObject aemObject) {
        return getLocaleByPath(turAemSource, aemObject.getPath());
    }

    public static void cleanCache() {
        responseHttpCache.clear();
    }
    public static Locale getLocaleFromContext(TurAemSourceContext turAemSourceContext, TurCmsContext context) {
        AemObject aemObject = (AemObject) context.getCmsObjectInstance();
        return getLocaleFromAemObject(turAemSourceContext, aemObject);
    }

    public static JSONObject getInfinityJson(String url, TurAemSourceContext turAemSourceContext) {
        String infinityJsonUrl = String.format(url.endsWith(TurAEMCommonAttrProcess.JSON) ? "%s%s" : "%s%s.infinity.json",
                turAemSourceContext.getUrl(), url);
        if (responseHttpCache.containsKey(infinityJsonUrl)) {
            log.info("Cached Response {}", infinityJsonUrl);
            return new JSONObject(responseHttpCache.get(infinityJsonUrl));
        } else {
            log.info("Request {}", infinityJsonUrl);
            return TurAEMCommonsUtils.getResponseBody(infinityJsonUrl, turAemSourceContext.getUsername(), turAemSourceContext.getPassword()).map(responseBody -> {
                if (TurAEMCommonsUtils.isResponseBodyJSONArray(responseBody) && !url.endsWith(TurAEMCommonAttrProcess.JSON)) {
                    return getInfinityJson(new JSONArray(responseBody).getString(0), turAemSourceContext);
                } else if (TurAEMCommonsUtils.isResponseBodyJSONObject(responseBody)) {
                    responseHttpCache.put(infinityJsonUrl, responseBody);
                    return new JSONObject(responseBody);
                }
                return new JSONObject();
            }).orElse(new JSONObject());
        }
    }

    public static void getJsonNodeToComponent(JSONObject jsonObject, StringBuilder components) {
        if (jsonObject.has(TurAEMCommonAttrProcess.JCR_TITLE) && jsonObject.get(TurAEMCommonAttrProcess.JCR_TITLE) instanceof String title) {
            components.append(title);
        } else if (jsonObject.has(TurAEMCommonAttrProcess.TEXT) && jsonObject.get(TurAEMCommonAttrProcess.TEXT) instanceof String text) {
            components.append(text);
        }
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(TurAEMCommonAttrProcess.JCR) && !key.startsWith(TurAEMCommonAttrProcess.SLING)
                    && (jsonObject.get(key) instanceof JSONObject jsonObjectNode)) {
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }
}
