/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TurWEMIndex {

    private static final ContextLogger log = ContextLogger.getLogger(TurWEMIndex.class);
    private static final String FILE_PROTOCOL = "file://";

    private TurWEMIndex() {
        throw new IllegalStateException("TurWEMIndex");
    }

    public static boolean indexCreate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        if ((mappingDefinitions != null) && (mo != null) && (mo instanceof ContentInstance)) {
            try {
                ContentInstance contentInstance = (ContentInstance) mo;

                String contentTypeName = contentInstance.getObjectType().getData().getName();
                
                // When there is related content but no associated site.
                if("Management Site".equals(siteName)) {
                    siteName = TuringUtils.getSiteName(contentInstance, config);
                }

                AsLocaleData asLocaleData = null;
                if ((contentInstance.getLocale() != null) && (contentInstance.getLocale().getAsLocale() != null)
                        && (contentInstance.getLocale().getAsLocale().getData() != null))
                    asLocaleData = contentInstance.getLocale().getAsLocale().getData();

                TurSNSiteConfig turSNSiteConfig = config.getSNSiteConfig(siteName, asLocaleData);
                if (isCTDIntoMapping(contentTypeName, config)) {
                    if (mappingDefinitions.isClassValidToIndex(contentInstance, config)) {
                        log.info(String.format(
                                "Viglet Turing indexer Processing Content Type: %s, WEM Site: %s, SNSite: %s, Locale: %s",
                                contentTypeName, siteName, turSNSiteConfig.getName(), turSNSiteConfig.getLocale()));
                        String xmlToIndex = generateXMLToIndex(contentInstance, config);
                        if (xmlToIndex.contains(FILE_PROTOCOL)) {
                            return generateZipImport(xmlToIndex, turSNSiteConfig, config);
                        } else {
                            return postIndex(xmlToIndex, turSNSiteConfig, config);
                        }
                    } else {
                        if (mappingDefinitions.hasClassValidToIndex(mo.getObjectType().getData().getName())
                                && mo.getContentManagementId() != null) {
                            TurWEMDeindex.indexDelete(mo.getContentManagementId(), config, siteName);
                        }

                    }
                } else {
                    if (log.isDebugEnabled())
                        log.debug(String.format(
                                "Mapping definition is not found in the mappingXML for the CTD and ignoring: %s",
                                contentTypeName));
                }
            } catch (Exception e) {
                log.error("Can't Create to Viglet Turing indexer.", e);
            }
        }
        return false;
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
            log.info(String.format("Indexing Content ID: %s (%s)", ci.getContentManagementId().getId(),
                    contentTypeName));
            xml.append(createXMLAttribute(GenericResourceHandlerConfiguration.ID_ATTRIBUTE,
                    ci.getContentManagementId().getId()));
            xml.append(createXMLAttribute(GenericResourceHandlerConfiguration.PROVIDER_ATTRIBUTE,
                    config.getProviderName()));
            List<TurAttrDef> attributeDefs = prepareAttributeDefs(ci, config, mappingDefinitions, ctdMappings);
            if (log.isDebugEnabled()) {
                attributeDefs.forEach(attributeDef ->
                        log.debug("attributeDef in generateXMLToIndex(): " + attributeDef.toString()));
            }

            addAttributeDefsToXML(xml, attributeDefs);
            addCategoriesToXML(ci, xml);

            xml.append("</document>");

            if (log.isDebugEnabled())
                log.debug(String.format("Viglet Turing XML content: %s", xml.toString()));
        } else {
            log.info(String.format("Mapping definition is not found in the mappingXML for the CTD: %s",
                    contentTypeName));
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
                                                         MappingDefinitions mappingDefinitions, CTDMappings ctdMappings) throws Exception {
        List<TurAttrDef> attributesDefs = new ArrayList<>();

        for (String tag : ctdMappings.getTagList()) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("generateXMLToIndex: Tag: %s", tag));
            }
            for (TuringTag turingTag : ctdMappings.getTuringTagMap().get(tag)) {
                if (tag != null && turingTag != null && turingTag.getTagName() != null) {

                    if (log.isDebugEnabled()) {
                        String debugRelation = turingTag.getSrcAttributeRelation() != null
                                ? turingTag.getSrcAttributeRelation().toString()
                                : null;
                        log.debug(String.format("Tag: %s, relation: %s, content Type: %s", turingTag.getTagName(),
                                debugRelation, turingTag.getSrcAttributeType()));
                    }

                    TurAttrDefContext turAttrDefContext = new TurAttrDefContext(ci, turingTag, config,
                            mappingDefinitions);
                    List<TurAttrDef> attributeDefsXML = TurWEMAttrXML.attributeXML(turAttrDefContext);

                    // Unique
                    if (turingTag.isSrcUniqueValues()) {

                        TurMultiValue multiValue = new TurMultiValue();
                        for (TurAttrDef turAttrDef : attributeDefsXML) {
                            for (String singleValue : turAttrDef.getMultiValue()) {
                                if (!multiValue.contains(singleValue)) {
                                    multiValue.add(singleValue);
                                }
                            }
                        }
                        TurAttrDef turAttrDefUnique = new TurAttrDef(turingTag.getTagName(), multiValue);
                        attributesDefs.add(turAttrDefUnique);
                    } else {
                        attributesDefs.addAll(attributeDefsXML);
                    }
                }
            }
        }
        return attributesDefs;
    }

    private static void addCategoriesToXML(ContentInstance ci, StringBuilder xml) {
        String[] classifications = ci.getTaxonomyClassifications();
        if (classifications != null && classifications.length > 0) {
            for (int i = 0; i < classifications.length; i++) {
                String wemClassification = classifications[i].substring(classifications[i].lastIndexOf("/") + 1);
                xml.append(createXMLAttribute("categories", wemClassification));
            }
        }
    }

    private static void addAttributeDefsToXML(StringBuilder xml, List<TurAttrDef> attributesDefs) {
        // Create xml of attributesDefs
        for (TurAttrDef turAttrDef : attributesDefs) {
            if (turAttrDef != null) {
                if (log.isDebugEnabled()) {
                    log.debug("AttributeDef - TagName: " + turAttrDef.getTagName());
                    for (String string : turAttrDef.getMultiValue()) {
                        log.debug("AttributeDef - Value: " + string);
                    }
                }
                if (turAttrDef.getMultiValue() != null && !turAttrDef.getMultiValue().isEmpty()) {
                    for (String value : turAttrDef.getMultiValue()) {
                        if ((value != null) && (value.trim().length() > 0))
                            xml.append(createXMLAttribute(turAttrDef.getTagName(), value));
                    }
                } else {
                    log.warn(String.format("No attributes to index of %s tag.", turAttrDef.getTagName()));
                }
            }
        }
    }

    public static boolean postIndex(String xml, TurSNSiteConfig turSNSiteConfig, IHandlerConfiguration config) {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            if (document != null) {
                Element element = document.getDocumentElement();

                NodeList nodes = element.getChildNodes();
                TurSNJobItems turSNJobItems = new TurSNJobItems();
                TurSNJobItem turSNJobItem = new TurSNJobItem();
                Map<String, Object> attributes = new HashMap<>();
                for (int i = 0; i < nodes.getLength(); i++) {

                    String nodeName = nodes.item(i).getNodeName();
                    if (attributes.containsKey(nodeName)) {
                        if (!(attributes.get(nodeName) instanceof ArrayList)) {
                            List<Object> attributeValues = new ArrayList<>();
                            attributeValues.add(attributes.get(nodeName));
                            attributeValues.add(nodes.item(i).getTextContent());

                            attributes.put(nodeName, attributeValues);
                            turSNJobItem.setAttributes(attributes);
                        } else {
                            @SuppressWarnings("unchecked")
                            List<Object> attributeValues = (List<Object>) attributes.get(nodeName);
                            attributeValues.add(nodes.item(i).getTextContent());
                            attributes.put(nodeName, attributeValues);
                        }
                    } else {
                        attributes.put(nodeName, nodes.item(i).getTextContent());

                    }
                }

                turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
                turSNJobItem.setLocale(turSNSiteConfig.getLocale());
                turSNJobItem.setAttributes(attributes);
                turSNJobItems.add(turSNJobItem);

                TuringUtils.sendToTuring(turSNJobItems, config, turSNSiteConfig);
            }

            log.info("Viglet Turing indexer Processed Content Type.");
            return true;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private static String createXMLAttribute(String tag, String value) {
        return String.format("<%1$s><![CDATA[%2$s]]></%1$s>", tag, value);
    }

    private static boolean generateZipImport(String xml, TurSNSiteConfig turSNSiteConfig, IHandlerConfiguration config) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));

            if (document != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(byteArrayOutputStream)) {
                    Element element = document.getDocumentElement();

                    NodeList nodes = element.getChildNodes();
                    TurSNJobItems turSNJobItems = new TurSNJobItems();
                    TurSNJobItem turSNJobItem = new TurSNJobItem();
                    Map<String, Object> attributes = new HashMap<>();
                    for (int i = 0; i < nodes.getLength(); i++) {
                        String randomFileName = UUID.randomUUID().toString();
                        String attributeValue = nodes.item(i).getTextContent();
                        String attributeName = nodes.item(i).getNodeName();
                        if (attributeValue.startsWith(FILE_PROTOCOL)) {
                            File file = new File(attributeValue.replace(FILE_PROTOCOL, ""));

                            ZipEntry entry = new ZipEntry(randomFileName);
                            entry.setTime(file.lastModified());
                            zos.putNextEntry(entry);
                            Files.copy(file.toPath(), zos);

                            attributeValue = FILE_PROTOCOL.concat(randomFileName);

                        }
                        if (attributes.containsKey(attributeName)) {
                            if (!(attributes.get(attributeName) instanceof ArrayList)) {
                                List<Object> attributeValues = new ArrayList<>();
                                attributeValues.add(attributes.get(attributeName));
                                attributeValues.add(attributeValue);
                                attributes.put(attributeName, attributeValues);
                            } else {
                                @SuppressWarnings("unchecked")
                                List<Object> attributeValues = (List<Object>) attributes.get(attributeName);
                                attributeValues.add(attributeValue);
                                attributes.put(attributeName, attributeValues);
                            }
                        } else {
                            attributes.put(attributeName, attributeValue);
                        }
                    }

                    turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
                    turSNJobItem.setLocale(turSNSiteConfig.getLocale());
                    turSNJobItem.setAttributes(attributes);
                    turSNJobItems.add(turSNJobItem);


                    ZipEntry entry = new ZipEntry("export.json");

                    zos.putNextEntry(entry);
                    zos.write(new ObjectMapper().writeValueAsString(turSNJobItems).getBytes());
                    zos.closeEntry();
                } catch (IOException ioe) {
                    log.error(ioe.getMessage(), ioe);
                }
                TuringUtils.sendToTuringAsZipFile(byteArrayOutputStream, config, turSNSiteConfig);

            }

            log.info("Viglet Turing indexer Processed Content Type.");
            return true;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}
