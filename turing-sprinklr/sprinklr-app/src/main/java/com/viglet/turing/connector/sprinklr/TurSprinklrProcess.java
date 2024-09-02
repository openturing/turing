package com.viglet.turing.connector.sprinklr;

import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNConstants;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;
import com.viglet.turing.connector.sprinklr.commons.ext.TurSprinklrExtInterface;
import com.viglet.turing.connector.sprinklr.commons.ext.TurSprinklrExtLocaleInterface;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrAttributeMapping;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.sprinklr.persistence.repository.TurSprinklrAttributeMappingRepository;
import com.viglet.turing.sprinklr.client.service.kb.TurSprinklrKBService;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrKBSearch;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrSecretKey;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrTokenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class TurSprinklrProcess {
    private final String turingUrl;
    private final String turingApiKey;
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final int jobSize;
    private final TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository;

    @Inject
    public TurSprinklrProcess(@Value("${turing.url}") String turingUrl,
                              @Value("${turing.apiKey}") String turingApiKey,
                              @Value("${turing.sprinklr.job.size}") int jobSize,
                              TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.jobSize = jobSize;
        this.turSprinklrAttributeMappingRepository = turSprinklrAttributeMappingRepository;
    }

    public void start(TurSprinklrSource turSprinklrSource) {
        reset();
        AtomicInteger kbPage = new AtomicInteger(0);
        TurSprinklrTokenService turSprinklrTokenService = new TurSprinklrTokenService(
                TurSprinklrSecretKey.builder()
                        .apiKey(turSprinklrSource.getApiKey())
                        .secretKey(turSprinklrSource.getSecretKey())
                        .environment(turSprinklrSource.getEnvironment())
                        .build());
        TurSprinklrAccessToken turSprinklrAccessToken = turSprinklrTokenService
                .getAccessToken();
        if (turSprinklrAccessToken != null) {
            while (true) {
                TurSprinklrKBSearch turSprinklrKBSearch = TurSprinklrKBService.run(turSprinklrAccessToken, kbPage.get());
                if (!(turSprinklrKBSearch == null)) {
                    List<TurSprinklrSearchResult> results = turSprinklrKBSearch.getData().getSearchResults();
                    if (results.isEmpty()) {
                        break;
                    } else {
                        results.forEach(searchResult -> {
                            getPage(turSprinklrSource, searchResult, turSprinklrAccessToken);
                            sendToTuringWhenMaxSize();
                            getInfoQueue();
                        });
                        kbPage.incrementAndGet();
                    }
                }
            }
        }
        if (turSNJobItems.size() > 0) {
            sendToTuring();
            getInfoQueue();
        }
    }

    private void reset() {
        turSNJobItems = new TurSNJobItems();
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
    }

    public void getPage(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult,
                        TurSprinklrAccessToken token) {
        log.info("{}: {}", searchResult.getId(), turSprinklrSource.getTurSNSites());
        addTurSNJobItems(turSprinklrSource, searchResult, token);

    }

    private void addTurSNJobItems(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult,
                                  TurSprinklrAccessToken token) {
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(turSprinklrSource.getTurSNSites()),
                getLocale(turSprinklrSource, searchResult, token),
                getJobItemAttributes(turSprinklrSource, token, searchResult));
        turSNJobItems.add(turSNJobItem);
    }

    public Locale getLocale(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult,
                            TurSprinklrAccessToken token) {
        return Optional.ofNullable(turSprinklrSource.getLocale())
                .orElseGet(() -> {
                    if (!StringUtils.isEmpty(turSprinklrSource.getLocaleClass())) {
                        return TurCustomClassCache.getCustomClassMap(turSprinklrSource.getLocaleClass())
                                .map(classInstance -> ((TurSprinklrExtLocaleInterface) classInstance)
                                        .consume(getTurSprinklrContext(searchResult, token))).orElse(Locale.US);
                    }
                    return Locale.US;
                });
    }

    private void sendToTuringWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }

    public Map<String, Object> getJobItemAttributes(TurSprinklrSource turSprinklrSource, TurSprinklrAccessToken token,
                                                    TurSprinklrSearchResult searchResult) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        turSprinklrAttributeMappingRepository.findByTurSprinklrSource(turSprinklrSource).ifPresent(source -> source.forEach(turSprinklrCustomClass ->
                Optional.ofNullable(turSprinklrCustomClass.getText()).ifPresentOrElse(text ->
                                turSNJobItemAttributes.put(turSprinklrCustomClass.getName(), text)
                        , () -> {
                            if (!StringUtils.isEmpty(turSprinklrCustomClass.getClassName()))
                                getCustomClass(searchResult, token, turSprinklrCustomClass)
                                        .ifPresent(turMultiValue -> turMultiValue.forEach(attributeValue -> {
                                            if (!StringUtils.isBlank(attributeValue)) {
                                                if (turSNJobItemAttributes.containsKey(turSprinklrCustomClass.getName())) {
                                                    addItemInExistingAttribute(attributeValue,
                                                            turSNJobItemAttributes, turSprinklrCustomClass.getName());
                                                } else {
                                                    addFirstItemToAttribute(turSprinklrCustomClass.getName(),
                                                            attributeValue, turSNJobItemAttributes);
                                                }
                                            }
                                        }));
                        }
                )));
        return turSNJobItemAttributes;
    }

    private Optional<TurMultiValue> getCustomClass(TurSprinklrSearchResult searchResult, TurSprinklrAccessToken token,
                                                   TurSprinklrAttributeMapping turSprinklrAttributeMapping) {
        return TurCustomClassCache.getCustomClassMap(turSprinklrAttributeMapping.getClassName())
                .flatMap(classInstance -> ((TurSprinklrExtInterface) classInstance)
                        .consume(getTurSprinklrContext(searchResult, token)));
    }

    public TurSprinklrContext getTurSprinklrContext(TurSprinklrSearchResult searchResult, TurSprinklrAccessToken token) {
        return TurSprinklrContext.builder()
                .searchResult(searchResult)
                .accessToken(token)
                .build();
    }

    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }

    private static void addItemInExistingAttribute(String attributeValue,
                                                   Map<String, Object> attributes,
                                                   String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList)
            addItemToArray(attributes, attributeName, attributeValue);
        else convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName, String attributeValue) {
        List<String> attributeValues = new ArrayList<>(((List<?>) attributes.get(attributeName))
                .stream().map(String.class::cast).toList());
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);

    }

    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
                                                           String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), null,
                            new TurApiKeyCredentials(turingApiKey)),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }
}
