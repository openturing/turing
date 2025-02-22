/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.commons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.UrlEscapers;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.bean.TurAemContext;
import com.viglet.turing.connector.aem.commons.bean.TurAemTargetAttrValueMap;
import com.viglet.turing.connector.aem.commons.config.IAemConfiguration;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtContentInterface;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtDeltaDate;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtDeltaDateInterface;
import com.viglet.turing.connector.aem.commons.mappers.TurAemContentDefinitionProcess;
import com.viglet.turing.connector.aem.commons.mappers.TurAemModel;
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
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.viglet.turing.connector.aem.commons.TurAemConstants.*;

@Slf4j
public class TurAemCommonsUtils {
    private TurAemCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    private static final int MAX_CACHE_SIZE = 1000;
    private static final Map<String, String> responseHttpCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };


    public static boolean isTypeEqualContentType(JSONObject jsonObject, TurAemSourceContext turAemSourceContext) {
        return jsonObject.has(JCR_PRIMARY_TYPE) &&
                jsonObject.getString(JCR_PRIMARY_TYPE)
                        .equals(turAemSourceContext.getContentType());
    }

    public static Optional<String> getSiteName(TurAemSourceContext turAemSourceContext, JSONObject jsonObject) {
        return getSiteName(jsonObject)
                .map(Optional::of)
                .orElseGet(() -> {
                    log.error("No site name the {} root path ({})", turAemSourceContext.getRootPath(),
                            turAemSourceContext.getId());
                    return Optional.empty();
                });
    }

    public static boolean usingContentTypeParameter(TurAemSourceContext turAemSourceContext) {
        return StringUtils.isNotBlank(turAemSourceContext.getContentType());
    }

    public static boolean isOnceConfig(String path, IAemConfiguration config) {
        if (StringUtils.isNotBlank(config.getOncePatternPath())) {
            Pattern p = Pattern.compile(config.getOncePatternPath());
            Matcher m = p.matcher(path);
            return m.lookingAt();
        }
        return false;
    }

    public static String configOnce(TurAemSourceContext turAemSourceContext) {
        return "%s/%s".formatted(turAemSourceContext.getId(), ONCE);
    }

    public static Date getDeltaDate(TurAemObject aemObject, TurAemSourceContext turAemSourceContext,
                                    TurAemContentDefinitionProcess turAemContentDefinitionProcess) {
        Date deltaDate = Optional.ofNullable(turAemContentDefinitionProcess.getDeltaClassName())
                .map(className -> TurCustomClassCache.getCustomClassMap(className)
                        .map(classInstance -> ((TurAemExtDeltaDateInterface) classInstance)
                                .consume(aemObject, turAemSourceContext))
                        .orElseGet(() -> defaultDeltaDate(aemObject, turAemSourceContext)))
                .orElseGet(() -> defaultDeltaDate(aemObject, turAemSourceContext));
        log.debug("Delta Date {} from {}", deltaDate.toString(), aemObject.getPath());
        return deltaDate;
    }


    private static Date defaultDeltaDate(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return new TurAemExtDeltaDate().consume(aemObject,
                turAemSourceContext);
    }

    public static TurAemTargetAttrValueMap runCustomClassFromContentType(TurAemModel turAemModel,
                                                                         TurAemObject aemObject,
                                                                         TurAemSourceContext turAemSourceContext) {
        return !StringUtils.isEmpty(turAemModel.getClassName()) ?
                TurCustomClassCache.getCustomClassMap(turAemModel.getClassName())
                        .map(customClassMap -> ((TurAemExtContentInterface) customClassMap)
                                .consume(aemObject, turAemSourceContext))
                        .orElseGet(TurAemTargetAttrValueMap::new) :
                new TurAemTargetAttrValueMap();
    }

    public static void addFirstItemToAttribute(String attributeName,
                                               String attributeValue,
                                               Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }

    @NotNull
    public static Date getDeltaDate(TurAemObject aemObject) {
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
                .filter(turSNAttributeSpec -> turSNAttributeSpec.getName() != null &&
                        turSNAttributeSpec.getName().equals(key))
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
                                                TurAemObject aemObject) {
        return getLocaleByPath(turAemSourceContext, aemObject.getPath());
    }

    public static Optional<TurAemObject> getAemObject(String url, TurAemSourceContext turAemSourceContext,
                                                      boolean cached) {
        return getInfinityJson(url, turAemSourceContext, cached).map(infinityJson -> new TurAemObject(url, infinityJson));
    }

    public static Optional<JSONObject> getInfinityJson(String url, TurAemSourceContext turAemSourceContext,
                                                       boolean cached) {
        String infinityJsonUrl = String.format(url.endsWith(JSON) ? "%s%s" : "%s%s.infinity.json",
                turAemSourceContext.getUrl(), url);
        return getResponseBody(infinityJsonUrl, turAemSourceContext, cached).map(responseBody -> {
            if (isResponseBodyJSONArray(responseBody) && !url.endsWith(JSON)) {
                return getInfinityJson(new JSONArray(responseBody).toList().getFirst().toString(),
                        turAemSourceContext, cached);
            } else if (isResponseBodyJSONObject(responseBody)) {
                return Optional.of(new JSONObject(responseBody));
            }
            return getInfinityJsonNotFound(infinityJsonUrl);
        }).orElseGet(() -> getInfinityJsonNotFound(infinityJsonUrl));

    }

    private static Optional<JSONObject> getInfinityJsonNotFound(String infinityJsonUrl) {
        log.info("Request Not Found {}", infinityJsonUrl);
        return Optional.empty();
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

    public static <T> Optional<T> getResponseBody(String url, TurAemSourceContext turAemSourceContext, Class<T> clazz,
                                                  boolean cached) {
        return getResponseBody(url, turAemSourceContext, cached).map(json ->
        {
            try {
                return new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readValue(json, clazz);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
            return null;
        });
    }

    public static Optional<String> getResponseBody(String url, TurAemSourceContext turAemSourceContext, boolean cached) {
        if (log.isDebugEnabled()) {
            responseHttpCache.forEach((k, v) -> log.debug("Cached Item Url: {}", k));
        }
        if (responseHttpCache.containsKey(url) && cached) {
            log.info("Cached Response {}", url);
            return Optional.of(responseHttpCache.get(url));
        } else {
            return getResponseBodyNoCache(url, turAemSourceContext, cached);
        }
    }

    private static @NotNull Optional<String> getResponseBodyNoCache(String url,
                                                                    TurAemSourceContext turAemSourceContext,
                                                                    boolean cached) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultHeaders(List.of(new BasicHeader(HttpHeaders.AUTHORIZATION,
                        basicAuth(turAemSourceContext.getUsername(), turAemSourceContext.getPassword()))))
                .build()) {
            HttpGet request = new HttpGet(URI.create(UrlEscapers.urlFragmentEscaper().escape(url)).normalize());
            String json = httpClient.execute(request, response -> {
                log.info("Request Status {} - {}", response.getCode(), url);
                HttpEntity entity = response.getEntity();
                return entity != null ? EntityUtils.toString(entity) : null;
            });
            if (TurCommonsUtils.isJSONValid(json)) {
                log.debug("Valid JSON - {}", url);
                if (cached) {
                    responseHttpCache.put(url, json);
                }
                return Optional.ofNullable(json);
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new TurRuntimeException(e);
        }
    }

    private static String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    public static void getJsonNodeToComponent(JSONObject jsonObject, StringBuilder components) {
        if (jsonObject.has(JCR_TITLE) && jsonObject.get(JCR_TITLE)
                instanceof String title) {
            components.append(title);
        } else if (jsonObject.has(TEXT) && jsonObject.get(TEXT)
                instanceof String text) {
            components.append(text);
        }
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(SLING)
                    && (jsonObject.get(key) instanceof JSONObject jsonObjectNode)) {
                getJsonNodeToComponent(jsonObjectNode, components);
            }
        });
    }

    public static Locale getLocaleFromContext(TurAemSourceContext turAemSourceContext, TurAemContext context) {
        TurAemObject aemObject = (TurAemObject) context.getCmsObjectInstance();
        return getLocaleFromAemObject(turAemSourceContext, aemObject);
    }

    public static void cleanCache() {
        responseHttpCache.clear();
    }
}
