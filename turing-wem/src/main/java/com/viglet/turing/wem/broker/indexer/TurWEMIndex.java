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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.beans.TurAttrDef;
import com.viglet.turing.wem.beans.TurAttrDefContext;
import com.viglet.turing.wem.beans.TurCTDMappingMap;
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.broker.attribute.TurWEMAttrXML;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.mappers.CTDMappings;
import com.viglet.turing.wem.mappers.MappingDefinitions;
import com.viglet.turing.wem.mappers.MappingDefinitionsProcess;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;

public class TurWEMIndex {

	private static final ContextLogger log = ContextLogger.getLogger(TurWEMIndex.class);

	private TurWEMIndex() {
		throw new IllegalStateException("TurWEMIndex");
	}

	public static boolean indexCreate(ManagedObject mo, IHandlerConfiguration config) {
		MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
		if ((mappingDefinitions != null) && (mo != null) && (mo instanceof ContentInstance)) {
			try {
				ContentInstance contentInstance = (ContentInstance) mo;

				String contentTypeName = contentInstance.getObjectType().getData().getName();

				AsLocaleData asLocaleData = null;
				if ((contentInstance.getLocale() != null) && (contentInstance.getLocale().getAsLocale() != null)
						&& (contentInstance.getLocale().getAsLocale().getData() != null))
					asLocaleData = contentInstance.getLocale().getAsLocale().getData();
				if (mappingDefinitions.isClassValidToIndex(contentInstance, config)) {
					log.info(String.format("Viglet Turing indexer Processing Content Type: %s", contentTypeName));
					return postIndex(generateXMLToIndex(contentInstance, config), asLocaleData, config);

				} else {
					if (mappingDefinitions.hasClassValidToIndex(mo.getObjectType().getData().getName())
							&& mo.getContentManagementId() != null) {
						TurWEMDeindex.indexDelete(mo, config);
					}
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

		if (ctdMappings == null) {
			log.error(String.format("Mapping definition is not found in the mappingXML for the CTD: %s",
					contentTypeName));
		} else {
			log.info(String.format("Indexing Content ID: %s (%s)", ci.getContentManagementId().getId(),
					contentTypeName));
			xml.append(createXMLAttribute("id", ci.getContentManagementId().getId()));

			List<TurAttrDef> attributeDefs = prepareAttributeDefs(ci, config, mappingDefinitions, ctdMappings);

			addAttributeDefsToXML(xml, attributeDefs);
			addCategoriesToXML(ci, xml);

			xml.append("</document>");

			if (log.isDebugEnabled())
				log.debug(String.format("Viglet Turing XML content: %s", xml.toString()));
		}
		return xml.toString();

	}

	private static List<TurAttrDef> prepareAttributeDefs(ContentInstance ci, IHandlerConfiguration config,
			MappingDefinitions mappingDefinitions, CTDMappings ctdMappings) throws Exception {
		List<TurAttrDef> attributesDefs = new ArrayList<>();

		for (String tag : ctdMappings.getTagList()) {
			if (log.isDebugEnabled()) {
				log.debug("generateXMLToIndex: TagList");
				for (String tags : ctdMappings.getTagList()) {
					log.debug("generateXMLToIndex: Tags: " + tags);
				}
			}
			for (TuringTag turingTag : ctdMappings.getTuringTagMap().get(tag)) {
				if (tag != null && turingTag != null && turingTag.getTagName() != null) {

					if (log.isDebugEnabled()) {
						String debugRelation = turingTag.getSrcAttributeRelation() != null
								? TuringUtils.listToString(turingTag.getSrcAttributeRelation())
								: null;
						log.debug(String.format("Tag:  %s, relation: %s, content Type: %s", turingTag.getTagName(),
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

	public static boolean postIndex(String xml, AsLocaleData asLocaleData, IHandlerConfiguration config) {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

			DocumentBuilder builder;
			Document document = null;
			builder = factory.newDocumentBuilder();
			document = builder.parse(new InputSource(new StringReader(xml)));

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
				turSNJobItem.setAttributes(attributes);
				turSNJobItems.add(turSNJobItem);

				TuringUtils.sendToTuring(turSNJobItems, config, asLocaleData);
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

}
