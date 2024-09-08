/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.wem.broker.indexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.beans.*;
import com.viglet.turing.wem.broker.attribute.TurWEMAttrXML;
import com.viglet.turing.wem.config.GenericResourceHandlerConfiguration;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.config.TurSNSiteConfig;
import com.viglet.turing.wem.mappers.CTDMappings;
import com.viglet.turing.wem.mappers.MappingDefinitions;
import com.viglet.turing.wem.mappers.MappingDefinitionsProcess;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class TurWEMIndex {
    private static final String FILE_PROTOCOL = "file://";
    private static final String MGMT_SITE = "Management Site";

    private TurWEMIndex() {
        throw new IllegalStateException("TurWEMIndex");
    }

    public static void indexCreate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        if ((mappingDefinitions != null) && (mo != null)) {
            if (mo instanceof Channel) {
                indexManagedObject((Channel) mo, config, siteName);
            } else if (mo instanceof ContentInstance) {
                indexContentInstance(mo, config, siteName, mappingDefinitions);
            }
        }
    }

    private static void indexContentInstance(ManagedObject mo, IHandlerConfiguration config, String siteName,
                                             MappingDefinitions mappingDefinitions) {
        try {
            ContentInstance contentInstance = (ContentInstance) mo;

            // When there is related content but no associated site.
            if (MGMT_SITE.equals(siteName)) {
                siteName = TuringUtils.getSiteName(contentInstance, config);
            }

            AsLocaleData asLocaleData = null;
            if (hasLocale(contentInstance))
                asLocaleData = contentInstance.getLocale().getAsLocale().getData();

            TurSNSiteConfig turSNSiteConfig = config.getSNSiteConfig(siteName, asLocaleData);
            String contentTypeName = contentInstance.getObjectType().getData().getName();
            if (isCTDIntoMapping(contentTypeName, config)) {
                if (mappingDefinitions.isClassValidToIndex(contentInstance, config)) {
                    log.info("Viglet Turing indexer Processing Content Type: {}, WEM Site: {}, SNSite: {}, Locale: {}",
                            contentTypeName, siteName, turSNSiteConfig.getName(), turSNSiteConfig.getLocale());
                    String xmlToIndex = generateXMLToIndex(contentInstance, config);
                    if (xmlToIndex.contains(FILE_PROTOCOL)) {
                        generateZipImport(xmlToIndex, turSNSiteConfig, config);
                    } else {
                        postIndex(xmlToIndex, turSNSiteConfig, config);
                    }
                } else {
                    if (mappingDefinitions.hasClassValidToIndex(mo.getObjectType().getData().getName())
                            && mo.getContentManagementId() != null) {
                        TurWEMDeindex.indexDelete(mo.getContentManagementId(), config, siteName);
                    }

                }
            } else {
                if (log.isDebugEnabled())
                    log.debug("Mapping definition is not found in the mappingXML for the CTD and ignoring: {}", contentTypeName);
            }
        } catch (Exception e) {
            log.error("Can't Create to Viglet Turing indexer.", e);
        }
    }

    private static boolean hasLocale(ContentInstance contentInstance) throws ApplicationException {
        return (contentInstance.getLocale() != null) && (contentInstance.getLocale().getAsLocale() != null)
                && (contentInstance.getLocale().getAsLocale().getData() != null);
    }

    private static void indexManagedObject(Channel mo, IHandlerConfiguration config, String siteName) {
        for (ManagedObject moReference : TuringUtils.getVgnExtPagesFromChannel(mo)) {
            indexCreate(moReference, config, siteName);
        }
    }

    // Generate XML To Index By ContentInstance
    public static String generateXMLToIndex(ContentInstance ci, IHandlerConfiguration config) throws Exception {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        if (log.isDebugEnabled())
            log.debug("Generating Viglet Turing XML for a content instance");

        String contentTypeName = ci.getObjectType().getData().getName();

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" ?><document>");

        TurCTDMappingMap mappings = mappingDefinitions.getMappingDefinitions();

        CTDMappings ctdMappings = mappings.get(contentTypeName);

        if (isCTDIntoMapping(contentTypeName, config)) {
            log.info("Indexing Content ID: {} ({})", ci.getContentManagementId().getId(), contentTypeName);
            xml.append(createXMLAttribute(GenericResourceHandlerConfiguration.ID_ATTRIBUTE,
                    ci.getContentManagementId().getId()));
            xml.append(createXMLAttribute(GenericResourceHandlerConfiguration.PROVIDER_ATTRIBUTE,
                    config.getProviderName()));
            List<TurAttrDef> attributeDefs = prepareAttributeDefs(ci, config, mappingDefinitions, ctdMappings);
            if (log.isDebugEnabled()) {
                attributeDefs.forEach(
                        attributeDef -> log.debug("attributeDef in generateXMLToIndex(): {}", attributeDef.toString()));
            }
            addAttributeDefsToXML(xml, attributeDefs);
            addCategoriesToXML(ci, xml);
            xml.append("</document>");

            if (log.isDebugEnabled())
                log.debug("Viglet Turing XML content: {}", xml);
        } else {
            log.info("Mapping definition is not found in the mappingXML for the CTD: {}", contentTypeName);
        }
        return xml.toString();

    }

    public static boolean isCTDIntoMapping(String contentTypeName, IHandlerConfiguration config) {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        TurCTDMappingMap mappings = mappingDefinitions.getMappingDefinitions();
        CTDMappings ctdMappings = mappings.get(contentTypeName);
        return ctdMappings != null;

    }

    public static int countCTDIntoMapping(IHandlerConfiguration config) {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        TurCTDMappingMap mappings = mappingDefinitions.getMappingDefinitions();

        return mappings.size();

    }

    private static List<TurAttrDef> prepareAttributeDefs(ContentInstance ci, IHandlerConfiguration config,
                                                         MappingDefinitions mappingDefinitions,
                                                         CTDMappings ctdMappings) throws Exception {
        List<TurAttrDef> attributesDefs = new ArrayList<>();
        for (String tag : ctdMappings.getTagList()) {
            log.debug("generateXMLToIndex: Tag: {}", tag);
            for (TuringTag turingTag : ctdMappings.getTuringTagMap().get(tag)) {
                if (hasTag(tag, turingTag)) {
                    prepareDebugLog(turingTag);
                    List<TurAttrDef> attributeDefsXML = TurWEMAttrXML
                            .attributeXML(new TurAttrDefContext(ci, turingTag, config,
                                    mappingDefinitions));
                    if (turingTag.isSrcUniqueValues()) {
                        attributesDefs.add(getUniqueValue(turingTag, attributeDefsXML));
                    } else {
                        attributesDefs.addAll(attributeDefsXML);
                    }
                }
            }
        }
        return attributesDefs;
    }

    private static boolean hasTag(String tag, TuringTag turingTag) {
        return tag != null && turingTag != null && turingTag.getTagName() != null;
    }

    private static TurAttrDef getUniqueValue(TuringTag turingTag, List<TurAttrDef> attributeDefsXML) {
        TurMultiValue multiValue = new TurMultiValue();
        attributeDefsXML.forEach(turAttrDef ->
                turAttrDef.getMultiValue()
                        .stream()
                        .filter(singleValue -> !multiValue.contains(singleValue))
                        .forEach(multiValue::add));
        return new TurAttrDef(turingTag.getTagName(), multiValue);

    }

    private static void prepareDebugLog(TuringTag turingTag) {
        if (log.isDebugEnabled()) {
            String debugRelation = turingTag.getSrcAttributeRelation() != null
                    ? turingTag.getSrcAttributeRelation().toString()
                    : null;
            log.debug("Tag: {}, relation: {}, content Type: {}",
                    turingTag.getTagName(), debugRelation, turingTag.getSrcAttributeType());
        }
    }

    private static void addCategoriesToXML(ContentInstance ci, StringBuilder xml) {
        String[] classifications = ci.getTaxonomyClassifications();
        if (classifications != null) {
            for (String classification : classifications) {
                String wemClassification = classification.substring(classification.lastIndexOf("/") + 1);
                xml.append(createXMLAttribute("categories", wemClassification));
            }
        }
    }

    private static void addAttributeDefsToXML(StringBuilder xml, List<TurAttrDef> attributesDefs) {
        // Create XML of attributesDefs
        attributesDefs
                .stream()
                .filter(Objects::nonNull)
                .forEach(turAttrDef -> {
                    attributeDefsToXMLDebugLog(turAttrDef);
                    if (hasMultiValue(turAttrDef)) {
                        xml.append(turAttrDef.getMultiValue()
                                .stream()
                                .filter(value -> (value != null) && !value.trim().isEmpty())
                                .map(value -> createXMLAttribute(turAttrDef.getTagName(), value))
                                .collect(Collectors.joining()));
                    } else {
                        log.warn("No attributes to index of {} tag.", turAttrDef.getTagName());
                    }
                });
    }

    private static void attributeDefsToXMLDebugLog(TurAttrDef turAttrDef) {
        if (log.isDebugEnabled()) {
            log.debug("AttributeDef - TagName: {}", turAttrDef.getTagName());
            for (String string : turAttrDef.getMultiValue()) {
                log.debug("AttributeDef - Value: {}", string);
            }
        }
    }

    private static boolean hasMultiValue(TurAttrDef turAttrDef) {
        return turAttrDef.getMultiValue() != null && !turAttrDef.getMultiValue().isEmpty();
    }

    public static void postIndex(String xml, TurSNSiteConfig turSNSiteConfig, IHandlerConfiguration config) {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            Document document = getImportXml(xml, factory);

            if (document != null) {
                Element element = document.getDocumentElement();
                NodeList nodes = element.getChildNodes();
                TurSNJobItems turSNJobItems = new TurSNJobItems();
                TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, List.of(turSNSiteConfig.getLocale()));
                Map<String, Object> attributes = new HashMap<>();
                for (int i = 0; i < nodes.getLength(); i++) {
                    String nodeName = nodes.item(i).getNodeName();
                    if (attributes.containsKey(nodeName)) {
                        if (!(attributes.get(nodeName) instanceof ArrayList)) {
                            attributeAsList(attributes, nodeName, nodes.item(i).getTextContent());
                        } else {
                            attributeAsObject(attributes, nodeName, nodes.item(i).getTextContent());
                        }
                    } else {
                        attributes.put(nodeName, nodes.item(i).getTextContent());

                    }
                }
                turSNJobItem.setAttributes(attributes);
                turSNJobItems.add(turSNJobItem);

                TuringUtils.sendToTuring(turSNJobItems, config, turSNSiteConfig);
            }

            log.info("Viglet Turing indexer Processed Content Type.");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void attributeAsObject(Map<String, Object> attributes, String nodeName, String nodes) {
        List<Object> attributeValues = (List<Object>) attributes.get(nodeName);
        attributeValues.add(nodes);
        attributes.put(nodeName, attributeValues);
    }

    private static void attributeAsList(Map<String, Object> attributes, String nodeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(nodeName));
        attributeValues.add(attributeValue);

        attributes.put(nodeName, attributeValues);

    }

    private static String createXMLAttribute(String tag, String value) {
        return String.format("<%1$s><![CDATA[%2$s]]></%1$s>", tag, value);
    }

    private static void generateZipImport(String xml, TurSNSiteConfig turSNSiteConfig,
                                          IHandlerConfiguration config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            Document document = getImportXml(xml, factory);
            if (document != null) {
                TuringUtils.sendToTuringAsZipFile(createZipFile(turSNSiteConfig, document), config, turSNSiteConfig);
            }
            log.info("Viglet Turing indexer Processed Content Type.");
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static ByteArrayOutputStream createZipFile(TurSNSiteConfig turSNSiteConfig, Document document) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(byteArrayOutputStream)) {
            Element element = document.getDocumentElement();
            NodeList nodes = element.getChildNodes();
            TurSNJobItems turSNJobItems = new TurSNJobItems();
            TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, List.of(turSNSiteConfig.getLocale()));
            Map<String, Object> attributes = new HashMap<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                String randomFileName = UUID.randomUUID().toString();
                String attributeValue = nodes.item(i).getTextContent();
                String attributeName = nodes.item(i).getNodeName();
                attributeValue = getFile(attributeValue, randomFileName, zos);
                if (attributes.containsKey(attributeName)) {
                    if (!(attributes.get(attributeName) instanceof ArrayList)) {
                        attributeAsList(attributes, attributeName, attributeValue);
                        turSNJobItem.setAttributes(attributes);
                    } else {
                        attributeAsObject(attributes, attributeName, attributeValue);
                    }
                } else {
                    attributes.put(attributeName, attributeValue);
                }
            }
            turSNJobItem.setAttributes(attributes);
            turSNJobItems.add(turSNJobItem);

            ZipEntry entry = new ZipEntry("export.json");

            zos.putNextEntry(entry);
            zos.write(new ObjectMapper().writeValueAsString(turSNJobItems).getBytes());
            zos.closeEntry();
        } catch (IOException ioe) {
            log.error(ioe.getMessage(), ioe);
        }
        return byteArrayOutputStream;
    }

    private static Document getImportXml(String xml, DocumentBuilderFactory factory)
            throws ParserConfigurationException, SAXException, IOException {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static String getFile(String attributeValue, String randomFileName, ZipOutputStream zos)
            throws IOException {
        if (attributeValue.startsWith(FILE_PROTOCOL)) {
            File file = new File(attributeValue.replace(FILE_PROTOCOL, ""));
            ZipEntry entry = new ZipEntry(randomFileName);
            entry.setTime(file.lastModified());
            zos.putNextEntry(entry);
            Files.copy(file.toPath(), zos);

            attributeValue = FILE_PROTOCOL.concat(randomFileName);

        }
        return attributeValue;
    }
}
