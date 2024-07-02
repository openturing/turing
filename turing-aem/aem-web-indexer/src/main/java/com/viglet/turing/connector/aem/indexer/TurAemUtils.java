package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSourceLocalePath;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

@Slf4j
public class TurAemUtils {

    protected static final Map<String, String> responseHttpCache = new HashMap<>();

    public static Locale getLocaleByPath(TurAemSource turAemSource, String path) {
        for (TurAemSourceLocalePath turAemSourceLocalePath : turAemSource.getLocalePaths()) {
            if (hasPath(turAemSourceLocalePath, path)) {
                return turAemSourceLocalePath.getLocale();
            }
        }
        return turAemSource.getDefaultLocale();
    }
    private static boolean hasPath(TurAemSourceLocalePath turAemSourceLocalePath, String path) {
        return path.startsWith(turAemSourceLocalePath.getPath());
    }
    public static Locale getLocaleFromAemObject(TurAemSource turAemSource,
                                                AemObject aemObject) {
        return getLocaleByPath(turAemSource, aemObject.getPath());
    }

    public static void cleanCache() {
        responseHttpCache.clear();
    }
    public static Locale getLocaleFromContext(TurAemContext turAemContext, TurCmsContext context) {
        AemObject aemObject = (AemObject) context.getCmsObjectInstance();
        return getLocaleFromAemObject(turAemContext.getSource(), aemObject);
    }

    public static JSONObject getInfinityJson(String url, TurAemContext context) {
        String infinityJsonUrl = String.format(url.endsWith(TurAEMCommonAttrProcess.JSON) ? "%s%s" : "%s%s.infinity.json",
                context.getUrl(), url);
        if (responseHttpCache.containsKey(infinityJsonUrl)) {
            log.info("Cached Response {}", infinityJsonUrl);
            return new JSONObject(responseHttpCache.get(infinityJsonUrl));
        } else {
            log.info("Request {}", infinityJsonUrl);
            return TurAEMCommonsUtils.getResponseBody(infinityJsonUrl, context.getUsername(), context.getPassword()).map(responseBody -> {
                if (TurAEMCommonsUtils.isResponseBodyJSONArray(responseBody) && !url.endsWith(TurAEMCommonAttrProcess.JSON)) {
                    return getInfinityJson(new JSONArray(responseBody).getString(0), context);
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
