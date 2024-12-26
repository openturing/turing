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

/**
 * The TurSprinklrProcess class is responsible for handling the indexing process of Sprinklr sources.
 * It interacts with various services and repositories to fetch data, process it, and add it to the job items list.
 *
 * <p>This class is annotated with {@code @Slf4j} for logging and {@code @Component} to indicate that it is a Spring component.</p>
 *
 * <p>Dependencies:</p>
 * <ul>
 *   <li>{@code TurSprinklrKBService} - Service for interacting with the Sprinklr Knowledge Base API.</li>
 *   <li>{@code TurSprinklrAttributeMappingRepository} - Repository for accessing attribute mappings.</li>
 *   <li>{@code TurSprinklrPluginContext} - Context for the Sprinklr plugin.</li>
 * </ul>
 *
 * <p>Methods:</p>
 * <ul>
 *   <li>{@code start(TurSprinklrSource, TurConnectorContext)} - Starts the indexing process for the given Sprinklr source.</li>
 *   <li>{@code getArticle(TurSprinklrSource, TurSprinklrSearchResult)} - Retrieves an article from the Sprinklr source and processes it.</li>
 *   <li>{@code getJobItemAttributes(TurSprinklrSource, TurSprinklrSearchResult)} - Returns a map of attribute names and values for a job item.</li>
 *   <li>{@code getLocale(TurSprinklrSource, TurSprinklrSearchResult)} - Retrieves the locale from the Sprinklr source or search result.</li>
 *   <li>{@code getFileAssets(TurSprinklrSearchResult, FileAssetsExtractor)} - Extracts file assets from the search result.</li>
 *   <li>{@code addFileAssetsToJobItems(List<FileAsset>, Locale, Collection<String>)} - Adds file assets to the job items list.</li>
 *   <li>{@code getTurSprinklrContext(TurSprinklrSearchResult)} - Builds a TurSprinklrContext object from a search result.</li>
 * </ul>
 *
 * <p>Utility Methods:</p>
 * <ul>
 *   <li>{@code addItemInExistingAttribute(String, Map<String, Object>, String)} - Adds an item to an existing attribute.</li>
 *   <li>{@code addItemToArray(Map<String, Object>, String, String)} - Adds an item to an array attribute.</li>
 *   <li>{@code convertAttributeSingleValueToArray(Map<String, Object>, String, String)} - Converts a single attribute value to an array of values.</li>
 *   <li>{@code dateAsChecksum(Date)} - Converts a Date object to a checksum string representation.</li>
 *   <li>{@code addFirstItemToAttribute(String, String, Map<String, Object>)} - Adds the first item to an attribute.</li>
 *   <li>{@code setAttribute(TurSprinklrAttributeMapping, String, Map<String, Object>)} - Sets an attribute value in the job item attributes map.</li>
 *   <li>{@code getCustomClass(TurSprinklrSearchResult, TurSprinklrAttributeMapping)} - Retrieves a custom class instance for attribute mapping.</li>
 * </ul>
 *
 * <p>Logging:</p>
 * <ul>
 *   <li>Logs information about the indexing process and retrieved articles.</li>
 *   <li>Logs debug information for custom class retrieval.</li>
 * </ul>
 *
 * <p>Annotations:</p>
 * <ul>
 *   <li>{@code @Slf4j} - Lombok annotation for logging.</li>
 *   <li>{@code @Component} - Spring annotation to indicate that this class is a Spring component.</li>
 *   <li>{@code @Inject} - Dependency injection annotation for constructor injection.</li>
 * </ul>
 */
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

    /**
     * Converts a single attribute value to an array of values and updates the attribute in the provided map.
     * If the attribute already exists in the map, it will be added to the list of values.
     *
     * @param attributes     the map containing attribute names and their corresponding values
     * @param attributeName  the name of the attribute to be converted
     * @param attributeValue the new value to be added to the attribute's list of values
     */
    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
                                                           String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    /**
     * Starts the indexing process for the given Sprinklr source.
     *
     * @param turSprinklrSource   the Sprinklr source configuration
     * @param turConnectorContext the connector context for managing the indexing process
     */
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
        for (FileAsset asset : fileAssets) {
            var turSNJobItemAttributes = asset.toMapAttributes();
            turConnectorContext.addJobItem(new TurSNJobItem(
                    TurSNJobAction.CREATE,
                    (List<String>) turSites,
                    locale,
                    turSNJobItemAttributes,
                    null,
                    dateAsChecksum(asset.getModificationDate())
            ));
        }
    }

    /**
     * Retrieves an article from the Sprinklr source and processes it.
     *
     * @param turSprinklrSource the source from which the article is retrieved
     * @param searchResult      the search result containing the article information
     */
    public void getArticle(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult) {
        log.info("{}: {}", searchResult.getId(), turSprinklrSource.getTurSNSites());
        addTurSNJobItem(turSprinklrSource, searchResult);

    }

    /**
     * Inserts a job in the job list parameter of this class (turSNJobItems)
     *
     * @param turSprinklrSource Source to extract the Semantic Navigation sites, locale and attributes
     * @param searchResult      Source to extract Locale and attributes
     */
    private void addTurSNJobItem(TurSprinklrSource turSprinklrSource, TurSprinklrSearchResult searchResult) {
        turConnectorContext.addJobItem(new TurSNJobItem(TurSNJobAction.CREATE,
                new ArrayList<>(turSprinklrSource.getTurSNSites()),
                getLocale(turSprinklrSource, searchResult),
                getJobItemAttributes(turSprinklrSource, searchResult),
                null,
                dateAsChecksum(searchResult.getModifiedTime())
        ));
    }

    /**
     * Converts a given Date object to a checksum string representation.
     * If the provided date is null, the current date and time is used.
     *
     * @param date the Date object to be converted to a checksum string.
     *             If null, the current date and time will be used.
     * @return a string representation of the date's time in milliseconds.
     */
    private String dateAsChecksum(Date date) {
        return date != null ?
                Long.toString(date.getTime()) :
                Long.toString(new Date().getTime());
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
        turSprinklrAttributeMappingRepository.findByTurSprinklrSource(turSprinklrSource)
                .ifPresent(mapping -> mapping.forEach(attribute ->
                Optional.ofNullable(attribute.getText()).ifPresentOrElse(text ->
                                turSNJobItemAttributes.put(attribute.getName(), text)
                        , () -> {
                            // If the ClassName field is filled in Export.json
                            if (!StringUtils.isEmpty(attribute.getClassName()))
                                getCustomClass(searchResult, attribute)
                                        .ifPresent(turMultiValue ->
                                                turMultiValue.forEach(attributeValue ->
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


    /**
     * Adds the first item to the specified attribute in the attributes map.
     *
     * @param attributeName  the name of the attribute to add
     * @param attributeValue the value of the attribute to add
     * @param attributes     the map of attributes where the attribute will be added
     */
    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }
}
