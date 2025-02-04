/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
import com.vignette.as.client.javabean.Channel;
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
import java.util.*;

public class TurWEMIndex {

	private static final ContextLogger log = ContextLogger.getLogger(TurWEMIndex.class.getName());

	private TurWEMIndex() {
		throw new IllegalStateException("TurWEMIndex");
	}

	public static boolean indexCreate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
		MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
		if ((mappingDefinitions != null) && (mo != null)) {
			if (mo instanceof Channel) {
				for (ManagedObject moReference : TuringUtils.getVgnExtPagesFromChannel((Channel) mo)) {
					indexCreate(moReference, config, siteName);
				}
			} else if (mo instanceof ContentInstance) {
				try {
					ContentInstance contentInstance = (ContentInstance) mo;

					String contentTypeName = contentInstance.getObjectType().getData().getName();

					// When there is related content but no associated site.
					if ("Management Site".equals(siteName)) {
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
							return postIndex(xmlToIndex, turSNSiteConfig, config);
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
				for (TurAttrDef attributeDef : attributeDefs) {
					log.debug("attributeDef in generateXMLToIndex(): " + attributeDef.toString());
				}
			}

			addAttributeDefsToXML(xml, attributeDefs);
			addCategoriesToXML(ci, xml);

			xml.append("</document>");

			if (log.isDebugEnabled())
				log.debug(String.format("Viglet Turing XML content: %s", xml));
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
		List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();

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
			for (String classification : classifications) {
				String wemClassification = classification.substring(classification.lastIndexOf("/") + 1);
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
				Map<String, Object> attributes = new HashMap<String, Object>();
				for (int i = 0; i < nodes.getLength(); i++) {

					String nodeName = nodes.item(i).getNodeName();
					if (attributes.containsKey(nodeName)) {
						if (!(attributes.get(nodeName) instanceof ArrayList)) {
							List<Object> attributeValues = new ArrayList<Object>();
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
		} catch (ParserConfigurationException e) {
			log.error(e.getMessage(), e);
		} catch (SAXException e) {
			log.error(e.getMessage(), e);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		return false;
	}

	private static String createXMLAttribute(String tag, String value) {
		return String.format("<%1$s><![CDATA[%2$s]]></%1$s>", tag, value);
	}
}
