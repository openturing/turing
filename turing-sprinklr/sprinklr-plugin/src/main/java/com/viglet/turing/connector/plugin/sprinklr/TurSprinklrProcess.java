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

package com.viglet.turing.connector.plugin.sprinklr;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.commons.plugin.TurConnectorContext;
import com.viglet.turing.connector.commons.plugin.TurConnectorSource;
import com.viglet.turing.connector.sprinklr.commons.kb.response.TurSprinklrSearchResult;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;
import com.viglet.turing.connector.sprinklr.commons.ext.TurSprinklrExtInterface;
import com.viglet.turing.connector.sprinklr.commons.ext.TurSprinklrExtLocaleInterface;
import com.viglet.turing.connector.plugin.sprinklr.persistence.model.TurSprinklrAttributeMapping;
import com.viglet.turing.connector.plugin.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.plugin.sprinklr.persistence.repository.TurSprinklrAttributeMappingRepository;
import com.viglet.turing.connector.sprinklr.commons.plugins.TurSprinklrPluginContext;
import com.viglet.turing.connector.plugin.sprinklr.utils.FileAsset;
import com.viglet.turing.connector.plugin.sprinklr.utils.FileAssetsExtractor;
import com.viglet.turing.connector.plugin.sprinklr.client.service.kb.TurSprinklrKBService;
import com.viglet.turing.connector.sprinklr.commons.kb.response.TurSprinklrKBSearch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class TurSprinklrProcess {
    public static final String SPRINKLR = "SPRINKLR";
    private final TurSprinklrKBService turSprinklrKBService;
    private final TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository;
    private TurConnectorContext turConnectorContext;

    private final TurSprinklrPluginContext pluginContext;

    @Inject
    public TurSprinklrProcess(TurSprinklrKBService turSprinklrKBService,
                              TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository,
                              TurSprinklrPluginContext pluginContext
                              ) {
        this.turSprinklrKBService = turSprinklrKBService;
        this.turSprinklrAttributeMappingRepository = turSprinklrAttributeMappingRepository;
        this.pluginContext = pluginContext;
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

    public void start(TurSprinklrSource turSprinklrSource, TurConnectorContext turConnectorContext) {
        this.turConnectorContext = turConnectorContext;
        this.turConnectorContext.startIndexing(new TurConnectorSource(turSprinklrSource.getId(),
                turSprinklrSource.getTurSNSites(), SPRINKLR, turSprinklrSource.getLocale()));
        // Index for the pagination parameter of the Knowledge Base API, it starts on 0
        AtomicInteger kbPage = new AtomicInteger(0);
            final var fileAssetExtractor = new FileAssetsExtractor(turConnectorContext.getTurServer());
            while (true) {
                TurSprinklrKBSearch turSprinklrKBSearch = turSprinklrKBService.run(kbPage.get());
                if (turSprinklrKBSearch != null) {
                    List<TurSprinklrSearchResult> results = turSprinklrKBSearch.getData().getSearchResults();
                    if (results.isEmpty()) {
                        break;
                    } else {
                        results.forEach(searchResult -> {
                            Locale resultLocale = searchResult.getLocale();
                            Collection<String> turSites = turSprinklrSource.getTurSNSites();

                            // Inserts new jobs into turSNJobItems
                            getArticle(turSprinklrSource, searchResult);

                            // Gets the assets attached to the search result and inserts into turSNJobItems.
                            List<FileAsset> assets = getFileAssets(searchResult, fileAssetExtractor);
                            addFileAssetsToJobItems(assets, resultLocale, turSites);
                        });
                        // Increment Index
                        kbPage.incrementAndGet();
                    }
                }
            }
        finished(turConnectorContext);
    }

    private static void finished(TurConnectorContext turConnectorContext) {
        turConnectorContext.finishIndexing();
    }

    /**
     * Extracts the file assets from the search result and returns a list of FileAsset objects.
     */
    private List<FileAsset> getFileAssets(TurSprinklrSearchResult searchResult, FileAssetsExtractor fileAssetExtractor) {
        final var fileAssets = fileAssetExtractor.extractFromLinkedAssets(searchResult);

        if (fileAssets == null || fileAssets.isEmpty()) {
            return Collections.emptyList();
        }
        return fileAssets;
    }

    /**
     * Adds the file assets to the job items list.
     */
    private void addFileAssetsToJobItems(List<FileAsset> fileAssets, Locale locale, Collection<String> turSites) {
        for (var asset : fileAssets) {
            var turSNJobItemAttributes = asset.toMapAttributes();
            turConnectorContext.addJobItem(new TurSNJobItem(
                    TurSNJobAction.CREATE,
                    (List<String>) turSites,
                    locale,
                    turSNJobItemAttributes
            ));
        }
    }

    public void getArticle(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult) {
        log.info("{}: {}", searchResult.getId(), turSprinklrSource.getTurSNSites());
        addTurSNJobItems(turSprinklrSource, searchResult);

    }

    /**
     * Inserts a job in the job list parameter of this class (turSNJobItems)
     *
     * @param turSprinklrSource Source to extract the Semantic Navigation sites, locale and attributes
     * @param searchResult      Source to extract Locale and attributes
     */
    private void addTurSNJobItems(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult) {
        turConnectorContext.addJobItem(new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(turSprinklrSource.getTurSNSites()),
                getLocale(turSprinklrSource, searchResult),
                getJobItemAttributes(turSprinklrSource, searchResult)));
    }

    /**
     * Get the Locale from inside turSprinklrSource entity, if it does not work, tries to get the locale from inside
     * locale_class column of turSprinklrSource entity, if also does not work, return Locale.US.
     *
     * @param turSprinklrSource the method will first try to get the Locale from turSprinklrSource.getLocale().
     * @param searchResult      is used to create a context where an attempt will be made to retrieve the locale.
     * @return Locale based on the parameters.
     */
    public Locale getLocale(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult) {
        /*
         Try to get extract the locale from turSprinklrSource.getLocale()
         Or else extracts the locale class (by default sprinklr-commons TurSprinklrExtLocal) from turSprinklrSource.
         If getCustomClassMap found a class, converts the result to an instance of TurSprinklrExtLocaleInterface.
         Then call the .consume method to get a Locale, if none of this works, then return Locale.US
        */
        return Optional.ofNullable(turSprinklrSource.getLocale())
                .orElseGet(() -> {
                    if (!StringUtils.isEmpty(turSprinklrSource.getLocaleClass())) {
                        return TurCustomClassCache.getCustomClassMap(turSprinklrSource.getLocaleClass())
                                .map(classInstance -> ((TurSprinklrExtLocaleInterface) classInstance)
                                        .consume(getTurSprinklrContext(searchResult))).orElse(Locale.US);
                    }
                    return Locale.US;
                });
    }

    /**
     * Returns a new HashMap of "Attribute(turing Field, Attribute Name from export.json) → Attribute Value". <p>
     * This method is used when sending a job to Turing.
     *
     * @param turSprinklrSource Is used to find the <b>turSprinklrAttributeMapping entity</b>, it represents
     *                          <code>export.json</code> file.
     * @param searchResult      If a <b>CustomClass</b> is defined by <code>export.json</code> file, the value will be extracted from <b>searchResult</b>
     * @return the created HashMap
     */
    public Map<String, Object> getJobItemAttributes(TurSprinklrSource turSprinklrSource,
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
         If not present, try to get the ClassName from `AttributeMapping`, instantiate it dynamically and consume it to get the value of the key.
        */
        turSprinklrAttributeMappingRepository.findByTurSprinklrSource(turSprinklrSource).ifPresent(mapping -> mapping.forEach(attribute ->
                Optional.ofNullable(attribute.getText()).ifPresentOrElse(text ->
                                turSNJobItemAttributes.put(attribute.getName(), text)
                        , () -> {
                            // Se o campo ClassName estiver preenchido no Export.json
                            if (!StringUtils.isEmpty(attribute.getClassName()))
                                getCustomClass(searchResult, attribute)
                                        .ifPresent(turMultiValue -> turMultiValue.forEach(attributeValue ->
                                                setAttribute(attribute, attributeValue, turSNJobItemAttributes)));
                        }
                )));
        return turSNJobItemAttributes;
    }

    private void setAttribute(TurSprinklrAttributeMapping attribute, String attributeValue, Map<String, Object> turSNJobItemAttributes) {
        if (!StringUtils.isBlank(attributeValue)) {
            if (turSNJobItemAttributes.containsKey(attribute.getName())) {
                addItemInExistingAttribute(attributeValue,
                        turSNJobItemAttributes, attribute.getName());
            } else {
                addFirstItemToAttribute(attribute.getName(),
                        attributeValue, turSNJobItemAttributes);
            }
        }
    }

    private Optional<TurMultiValue> getCustomClass(TurSprinklrSearchResult searchResult,
                                                   TurSprinklrAttributeMapping turSprinklrAttributeMapping) {
        log.debug(getTurSprinklrContext(searchResult).getPluginContext().toString());
        return TurCustomClassCache.getCustomClassMap(turSprinklrAttributeMapping.getClassName())
                .flatMap(classInstance -> ((TurSprinklrExtInterface) classInstance)
                        .consume(getTurSprinklrContext(searchResult)));
    }

    /**
     * Builds a TurSprinklrContext object from a searchResult and a token object
     */
    public TurSprinklrContext getTurSprinklrContext(TurSprinklrSearchResult searchResult) {
        return TurSprinklrContext.builder()
                .searchResult(searchResult)
                .pluginContext(pluginContext)
                .build();
    }

    // Used only for getJobItemAttributes
    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }
}
