package com.viglet.turing.connector.aem.commons;

import com.google.common.net.UrlEscapers;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtContentInterface;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import com.viglet.turing.connector.cms.mappers.TurCmsModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.*;

@Slf4j
public class TurAEMCommonsUtils {
    private TurAEMCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    protected static final Map<String, String> responseHttpCache = new HashMap<>();
    public static final String JCR_CONTENT = "jcr:content";
    public static final String JCR_TITLE = "jcr:title";


    public static TurCmsTargetAttrValueMap runCustomClassFromContentType(TurCmsModel turCmsModel, AemObject aemObject,
                                                                         TurAemSourceContext turAemSourceContext) {
        return !StringUtils.isEmpty(turCmsModel.getClassName()) ?
                TurCustomClassCache.getCustomClassMap(turCmsModel.getClassName())
                        .map(customClassMap -> ((ExtContentInterface) customClassMap)
                                .consume(aemObject, turAemSourceContext)).orElseGet(TurCmsTargetAttrValueMap::new) :
                new TurCmsTargetAttrValueMap();
    }

    public static void addFirstItemToAttribute(String attributeName,
                                               String attributeValue,
                                               Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }

    @NotNull
    public static Date getDeltaDate(AemObject aemObject) {
        if (aemObject.getLastModified() != null)
            return aemObject.getLastModified().getTime();
        if (aemObject.getCreatedDate() != null)
            return aemObject.getCreatedDate().getTime();
        return new Date();
    }

    public static List<TurSNAttributeSpec> getDefinitionFromModel(List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                  Map<String, Object> targetAttrMap) {
        List<TurSNAttributeSpec> turSNAttributeSpecFromModelList = new ArrayList<>();
        targetAttrMap.forEach((key, value) -> turSNAttributeSpecList.stream()
                .filter(turSNAttributeSpec -> turSNAttributeSpec.getName().equals(key))
                .findFirst().ifPresent(turSNAttributeSpecFromModelList::add));
        return turSNAttributeSpecFromModelList;
    }

    public static Optional<String> getSiteName(JSONObject jsonSite) {
        if (jsonSite.has(JCR_CONTENT) && jsonSite.getJSONObject(JCR_CONTENT).has(JCR_TITLE)) {
            return jsonSite.getJSONObject(JCR_CONTENT).getString(JCR_TITLE).describeConstable();
        }
        return Optional.empty();
    }

    public static boolean checkIfFileHasNotImageExtension(String s) {
        String[] imageExtensions = {".jpg", ".png", ".jpeg", ".svg", ".webp"};
        return Arrays.stream(imageExtensions).noneMatch(suffix -> s.toLowerCase().endsWith(suffix));
    }

    public static void addItemInExistingAttribute(String attributeValue,
                                                  Map<String, Object> attributes,
                                                  String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList)
            addItemToArray(attributes, attributeName, attributeValue);
        else convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
    }


    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
                                                           String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName, String attributeValue) {
        List<String> attributeValues = new ArrayList<>(((List<?>) attributes.get(attributeName))
                .stream().map(String.class::cast).toList());
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);

    }

    @NotNull
    public static List<TurSNJobAttributeSpec> castSpecToJobSpec(List<TurSNAttributeSpec> turSNAttributeSpecList) {
        return turSNAttributeSpecList.stream()
                .filter(Objects::nonNull)
                .map(TurSNJobAttributeSpec.class::cast)
                .toList();
    }

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

    public static Optional<JSONObject> getInfinityJson(String url, TurAemSourceContext turAemSourceContext) {
        return getInfinityJson(url, turAemSourceContext.getUrl(), turAemSourceContext.getUsername(), turAemSourceContext.getPassword());
    }

    public static Optional<JSONObject> getInfinityJson(String originalUrl, String hostAndPort, String username, String password) {
        String infinityJsonUrl = String.format(originalUrl.endsWith(TurAEMAttrProcess.JSON) ? "%s%s" : "%s%s.infinity.json",
                hostAndPort, originalUrl);
        if (responseHttpCache.containsKey(infinityJsonUrl)) {
            log.info("Cached Response {}", infinityJsonUrl);
            return Optional.of(new JSONObject(responseHttpCache.get(infinityJsonUrl)));
        } else {
            log.info("Request {}", infinityJsonUrl);
            return TurAEMCommonsUtils.getResponseBody(infinityJsonUrl, username, password).map(responseBody -> {
                if (TurAEMCommonsUtils.isResponseBodyJSONArray(responseBody) && !originalUrl.endsWith(TurAEMAttrProcess.JSON)) {
                    JSONArray jsonArray = new JSONArray(responseBody);
                    return getInfinityJson(jsonArray.getString(0), hostAndPort, username, password);
                } else if (TurAEMCommonsUtils.isResponseBodyJSONObject(responseBody)) {
                    responseHttpCache.put(infinityJsonUrl, responseBody);
                    return Optional.of(new JSONObject(responseBody));
                }
                return Optional.<JSONObject>empty();
            }).orElse(Optional.empty());
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
            throw new TurRuntimeException(e);
        }
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
