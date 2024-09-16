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
import com.viglet.turing.connector.sprinklr.utils.FileAsset;
import com.viglet.turing.connector.sprinklr.utils.FileAssetsExtractor;
import com.viglet.turing.sprinklr.client.service.kb.TurSprinklrKBService;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrKBSearch;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrSecretKey;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrTokenService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value; // Gets the value from application.properties
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
    private final int jobSize;
    private final TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository;
    /**
     * Represents the jobs that will be sent to Turing API
     */
    private TurSNJobItems turSNJobItems = new TurSNJobItems();

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

    public void start(TurSprinklrSource turSprinklrSource) {
        reset();
        // Index for the pagination parameter of the Knowledge Base API, it starts on 0
        AtomicInteger kbPage = new AtomicInteger(0);

        // Get a token for the API
        TurSprinklrTokenService turSprinklrTokenService = new TurSprinklrTokenService(
                TurSprinklrSecretKey.builder()
                        .apiKey(turSprinklrSource.getApiKey())
                        .secretKey(turSprinklrSource.getSecretKey())
                        .environment(turSprinklrSource.getEnvironment())
                        .build()
        );
        TurSprinklrAccessToken turSprinklrAccessToken = turSprinklrTokenService.getAccessToken();

        if (turSprinklrAccessToken != null) {
            while (true) {
                //TODO Maybe we can use TurSprinklrSearchData instead of turSprinklrKBSearchResult?
                TurSprinklrKBSearch turSprinklrKBSearch = TurSprinklrKBService.run(turSprinklrAccessToken, kbPage.get());

                if (turSprinklrKBSearch != null) {
                    List<TurSprinklrSearchResult> results = turSprinklrKBSearch.getData().getSearchResults();

                    if (results.isEmpty()) {
                        break;
                    } else {
                        results.forEach(searchResult -> {


                            // Inserts new jobs into turSNJobItems parameter
                            getArticle(turSprinklrSource, searchResult, turSprinklrAccessToken);

                            List<FileAsset> assets = getFileAssets(searchResult);
                            addFileAssetsToJobItens(assets, resultLocale);

                            // Quando o tamanho de turSNJobItems alcançar o JobSize definido, envia para o turing.
                            sendToTuringWhenMaxSize();

                            getInfoQueue();
                        });
                        // Increment Index
                        kbPage.incrementAndGet();
                    }
                }
            }
        }
        if (turSNJobItems.size() > 0) {
            // Envia os últimos jobs restantes.
            sendToTuring();
            getInfoQueue();
        }
    }

    private void addFileAssetsToJobItens(List<FileAsset> fileAssets, Locale locale) {
        for (var asset : fileAssets) {
            var turSNJobItemAttributes = asset.toMapAttributes();

            TurSNJobItem turSNJobItem = new TurSNJobItem(
                    TurSNJobAction.CREATE,
                    turSprinklrSource.getTurSNSites().stream().toList(),
                    getLocale(turSprinklrSource, searchResult, turSprinklrAccessToken),
                    turSNJobItemAttributes
            );
            turSNJobItems.add(turSNJobItem);
        }
    }

    private List<FileAsset> getFileAssets(TurSprinklrSearchResult searchResult) {
        var fileAssetExtractor = new FileAssetsExtractor(turingUrl, turingApiKey);
        var fileAssets = fileAssetExtractor.extractFromLinkedAssets(searchResult);

        if(fileAssets == null || fileAssets.size() == 0){
            return Collections.emptyList();
        }
        return fileAssets;


    }

    /**
     * Clears the List of jobs in Turing API
     */
    private void reset() {
        turSNJobItems = new TurSNJobItems();
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
    }

    //TODO ask why turSprinklrAccessToken is being used
    public void getArticle(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult,
                           TurSprinklrAccessToken token) {
        log.info("{}: {}", searchResult.getId(), turSprinklrSource.getTurSNSites());
        addTurSNJobItems(turSprinklrSource, searchResult, token);

    }

    /**
     * Inserts a job in the job list parameter of this class (turSNJobItems)
     *
     * @param turSprinklrSource Source to extract the Semantic Navigation sites, locale and attributes
     * @param searchResult      Source to extract Locale and attributes
     * @param token             N/A
     */
    private void addTurSNJobItems(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult,
                                  TurSprinklrAccessToken token) {
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(turSprinklrSource.getTurSNSites()),
                getLocale(turSprinklrSource, searchResult, token),
                getJobItemAttributes(turSprinklrSource, token, searchResult));
        turSNJobItems.add(turSNJobItem);
    }

    /**
     * Get the Locale from inside turSprinklrSource entity, if it does not work, tries to get the locale from inside
     * locale_class column of turSprinklrSource entity, if also does not work, return Locale.US.
     *
     * @param turSprinklrSource the method will first try to get the Locale from turSprinklrSource.getLocale().
     * @param searchResult      is used to create a context where an attempt will be made to retrieve the locale.
     * @param token             N/A
     * @return Locale based on the parameters.
     */
    public Locale getLocale(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult,
                            TurSprinklrAccessToken token) {
        /*
         Try to get extract the locale from turSprinklrSource.getLocale()
         Or else extracts the locale class (by default sprinklr-commons TurSprinklrExtLocal) from turSprinklrSource.
         If getCustomClassMap found a class, converts the result to a instance of TurSprinklrExtLocaleInterface.
         Then calls the .consume method to get a Locale, if none of this works, then return Locale.US
        */
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

    /**
     * Returns a new HashMap of "Attribute(turing Field, Attribute Name from export.json) -> Attribute Value". <p>
     * This method is used when sending a job to Turing.
     *
     * @param turSprinklrSource Is used to find the <b>turSprinklrAttributeMapping entity</b>, it represents
     *                          <code>export.json</code> file.
     * @param token             N/A
     * @param searchResult      If a <b>CustomClass</b> is defined by <code>export.json</code> file, the value will be extracted from <b>searchResult</b>
     * @return the created HashMap
     */
    public Map<String, Object> getJobItemAttributes(TurSprinklrSource turSprinklrSource, TurSprinklrAccessToken token,
                                                    TurSprinklrSearchResult searchResult) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        /* Example

              NAME         |      TEXT(VALUE)
            source_apps    |    SPRINKLR (Hardcoded)
            created_date   |    19-02-2015 (retrieved using a custom class)

         */

        /*
         Will retrieve the objects from 'turSprinklrAttributeMapping' entity, then for each one of the objects that this entity contains. Will get
         the 'text' attribute of this object.
         If the 'text' attribute is present, then will create a new key in turSNJobItemAttributes with the `name` attribute of this same object. The
         value of this key will be the 'text'.
         If not present, tries to get the ClassName from `AttributeMapping`, instantiate it dynamically and consumes it to get the value of the key.
        */
        turSprinklrAttributeMappingRepository.findByTurSprinklrSource(turSprinklrSource).ifPresent(mapping -> mapping.forEach(attribute ->
                Optional.ofNullable(attribute.getText()).ifPresentOrElse(text ->
                                turSNJobItemAttributes.put(attribute.getName(), text)
                        , () -> {
                            // Se o campo ClassName estiver preenchido no Export.json
                            if (!StringUtils.isEmpty(attribute.getClassName()))
                                getCustomClass(searchResult, token, attribute)
                                        .ifPresent(turMultiValue -> turMultiValue.forEach(attributeValue -> {
                                            if (!StringUtils.isBlank(attributeValue)) {
                                                if (turSNJobItemAttributes.containsKey(attribute.getName())) {
                                                    addItemInExistingAttribute(attributeValue,
                                                            turSNJobItemAttributes, attribute.getName());
                                                } else {
                                                    addFirstItemToAttribute(attribute.getName(),
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

    /**
     * Builds a TurSprinklrContext object from a searchResult and a token object
     */
    public TurSprinklrContext getTurSprinklrContext(TurSprinklrSearchResult searchResult, TurSprinklrAccessToken token) {
        return TurSprinklrContext.builder()
                .searchResult(searchResult)
                .accessToken(token)
                .build();
    }

    // Used only for getJobItemAttributes
    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }


    /**
     * Push current turSNJobItems to turing.
     */
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
