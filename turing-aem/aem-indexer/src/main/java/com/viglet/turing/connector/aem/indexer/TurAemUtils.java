package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

@Slf4j
public class TurAemUtils {

    protected static final Map<String, String> responseHttpCache = new HashMap<>();

    public static Locale getLocaleFromAemObject(AemHandlerConfiguration config, AemObject aemObject) {
        return LocaleUtils.toLocale(config.getLocaleByPath(config.getDefaultSNSiteConfig().getName(),
                aemObject.getPath()));
    }

    public static Locale getLocaleFromContext(TurCmsContext context) {
        AemHandlerConfiguration config = (AemHandlerConfiguration) context.getConfiguration();
        AemObject aemObject = (AemObject) context.getCmsObjectInstance();
        return getLocaleFromAemObject(config, aemObject);
    }

    public static JSONObject getInfinityJson(String url, TurAEMIndexerTool tool) {
        return getInfinityJson(url, tool.getHostAndPort(), tool.getUsername(), tool.getPassword());
    }

    public static JSONObject getInfinityJson(String originalUrl, String hostAndPort, String username, String password) {
        String infinityJsonUrl = String.format(originalUrl.endsWith(TurAEMCommonAttrProcess.JSON) ? "%s%s" : "%s%s.infinity.json",
                hostAndPort, originalUrl);
        if (responseHttpCache.containsKey(infinityJsonUrl)) {
            log.info(STR."Cached Response \{infinityJsonUrl}");
            return new JSONObject(responseHttpCache.get(infinityJsonUrl));
        } else {
            log.info(STR."Request \{infinityJsonUrl}");
            return TurAEMCommonsUtils.getResponseBody(infinityJsonUrl, username, password).map(responseBody -> {
                if (TurAEMCommonsUtils.isResponseBodyJSONArray(responseBody) && !originalUrl.endsWith(TurAEMCommonAttrProcess.JSON)) {
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




    public static void getJsonNodeToComponent(JSONObject jsonObject, StringBuilder components) {
        if (jsonObject.has(TurAEMCommonAttrProcess.JCR_TITLE) && jsonObject.get(TurAEMCommonAttrProcess.JCR_TITLE)
                instanceof String title) {
            components.append(title);
        } else if (jsonObject.has(TurAEMCommonAttrProcess.TEXT) && jsonObject.get(TurAEMCommonAttrProcess.TEXT)
                instanceof String text) {
            components.append(text);
        }
        jsonObject.toMap().forEach((key, _) -> {
            if (!key.startsWith(TurAEMCommonAttrProcess.JCR) && !key.startsWith(TurAEMCommonAttrProcess.SLING)
                    && (jsonObject.get(key) instanceof JSONObject jsonObjectNode)) {
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }
}
