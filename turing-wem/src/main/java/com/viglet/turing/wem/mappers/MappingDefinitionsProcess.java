/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
package com.viglet.turing.wem.mappers;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import com.viglet.turing.wem.beans.TurCTDMappingMap;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.beans.TuringTagMap;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.logging.context.ContextLogger;

// Open and process Mappping XML File structure
public class MappingDefinitionsProcess {
	private static final ContextLogger log = ContextLogger.getLogger(MappingDefinitionsProcess.class);

	private MappingDefinitionsProcess() {
		throw new IllegalStateException("MappingDefinitionsProcess");
	}

	public static MappingDefinitions loadMappings(String resourceXml) {
		TurCTDMappingMap mappings = null;

		try {
			DocumentBuilderFactory dlf = DocumentBuilderFactory.newInstance();
			//dlf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			//dlf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			DocumentBuilder db = dlf.newDocumentBuilder();

			File f = new File(resourceXml);
			if (f.isFile() && f.canRead()) {
				InputStream resourceInputStream = new FileInputStream(resourceXml);

				Document document = db.parse(resourceInputStream);
				Element rootElement = document.getDocumentElement();

				// Loading mapping definitions
				mappings = readCTDMappings(rootElement);

			} else {
				return null;
			}

		} catch (Exception e) {
			log.error("Error when loading mappings", e);
			return null;
		}
		return new MappingDefinitions(resourceXml, mappings);
	}

	/**
	 * Loading mapping definitions
	 * 
	 * @param rootElement
	 * @return TurCTDMappingMap
	 */
	public static TurCTDMappingMap readCTDMappings(Element rootElement) {
		TurCTDMappingMap mappings = new TurCTDMappingMap();
		List<TuringTag> commonIndexAttrs = readCommonIndexAttrs(rootElement);
		readMappingDefinitions(rootElement, mappings, commonIndexAttrs);
		return mappings;
	}

	private static List<TuringTag> readCommonIndexAttrs(Element rootElement) {
		return readIndexAttributeMappings(rootElement, TurXMLConstant.TAG_COMMON_INDEX_DATA);
	}

	private static void readMappingDefinitions(Element rootElement, TurCTDMappingMap mappings,
			List<TuringTag> commonIndexAttrs) {
		// Get <mappingdefinition/> List
		NodeList contentTypes = rootElement.getElementsByTagName(TurXMLConstant.TAG_MAPPING_DEF);

		for (int i = 0; i < contentTypes.getLength(); i++) {
			readMappingDefinition(mappings, commonIndexAttrs, contentTypes, i);
		}
	}

	private static void readMappingDefinition(TurCTDMappingMap mappings, List<TuringTag> commonIndexAttrs,
			NodeList contentTypes, int i) {
		Element mappingDefinition = (Element) contentTypes.item(i);

		// If it has content type attribute
		if (mappingDefinition.hasAttribute(TurXMLConstant.TAG_ATT_MAPPING_DEF)) {
			String ctdXmlName = mappingDefinition.getAttribute(TurXMLConstant.TAG_ATT_MAPPING_DEF);

			// Read <index-attr/>
			List<TuringTag> indexAttrs = readIndexAttributeMappings((Element) contentTypes.item(i),
					TurXMLConstant.TAG_INDEX_DATA);

			// Merge CommonIndexAttrs into IndexAttrs
			TuringTagMap turingTagMap = mergeCommonAttrs(commonIndexAttrs, indexAttrs);

			// Add attributes common and index attributes into CTDMapping
			CTDMappings ctdMapping = new CTDMappings(turingTagMap);

			// Set isValidToIndex
			if (mappingDefinition.hasAttribute(TurXMLConstant.TAG_ATT_CLASS_VALID_TOINDEX))
				ctdMapping.setClassValidToIndex(
						mappingDefinition.getAttribute(TurXMLConstant.TAG_ATT_CLASS_VALID_TOINDEX));

			/// HashMap of CTDs
			mappings.put(ctdXmlName, ctdMapping);
			if (log.isDebugEnabled()) {
				debugReadCTDMappings(mappings);
			}
		}
	}

	private static void debugReadCTDMappings(TurCTDMappingMap mappings) {
		int index = 0;
		for (Entry<String, CTDMappings> mappingEntry : mappings.entrySet()) {
			log.debug(String.format("%d - MappingEntry CTD : %s", index, mappingEntry.getKey()));
			for (Entry<String, ArrayList<TuringTag>> turingTagEntry : mappingEntry.getValue().getTuringTagMap()
					.entrySet()) {
				log.debug("TuringTag Key (TagName): " + turingTagEntry.getKey());
				for (TuringTag turingTag : turingTagEntry.getValue()) {
					log.debug("TuringTag Item - getTagName : " + turingTag.getTagName());
					log.debug("TuringTag Item - getSrcAttributeType : " + turingTag.getSrcAttributeType());
					log.debug("TuringTag Item - getSrcClassName : " + turingTag.getSrcClassName());
					log.debug("TuringTag Item - getSrcXmlName : " + turingTag.getSrcXmlName());
					log.debug("TuringTag Item - getSrcAttributeRelation : " + turingTag.getSrcAttributeRelation());
					log.debug("TuringTag Item - getSrcMandatory : " + turingTag.getSrcMandatory());
				}
			}
			index++;
		}
	}

	public static TuringTagMap mergeCommonAttrs(List<TuringTag> commonIndexAttrs, List<TuringTag> indexAttrs) {

		TuringTagMap indexAttrsMapMerged = new TuringTagMap();

		Map<String, TuringTag> commonIndexAttrMap = new HashMap<>();
		for (TuringTag turingTag : commonIndexAttrs)
			commonIndexAttrMap.put(turingTag.getTagName(), turingTag);

		for (TuringTag turingTag : indexAttrs) {
			if (turingTag != null) {
				if (commonIndexAttrs != null && turingTag.getSrcClassName() == null
						&& commonIndexAttrMap.get(turingTag.getTagName()) != null) {
					// Common always have one item
					// Add ClassName of Common into Index, if it doesn't have ClassName
					turingTag.setSrcClassName(commonIndexAttrMap.get(turingTag.getTagName()).getSrcClassName());
				}

				if (!indexAttrsMapMerged.containsKey(turingTag.getTagName()))
					indexAttrsMapMerged.put(turingTag.getTagName(), new ArrayList<>());
				indexAttrsMapMerged.get(turingTag.getTagName()).add(turingTag);
			}
		}

		// Add only Mandatory Attributes
		for (TuringTag commonTuringTag : commonIndexAttrs) {
			// Doesn't repeat tags that exist in Ctd
			if (commonTuringTag.getSrcMandatory() && !indexAttrsMapMerged.containsKey(commonTuringTag.getTagName())) {
				ArrayList<TuringTag> turingTags = new ArrayList<>();
				turingTags.add(commonTuringTag);
				indexAttrsMapMerged.put(commonTuringTag.getTagName(), turingTags);
			}
		}

		return indexAttrsMapMerged;
	}

	// Read <index-attrs/> or <common-index-attrs/>
	public static List<TuringTag> readIndexAttributeMappings(Element rootElement, String genericIndexAttrsTag) {
		List<TuringTag> turingTagMap = new ArrayList<>();
		NodeList attributes = rootElement.getElementsByTagName(genericIndexAttrsTag);
		for (int i = 0; i < attributes.getLength(); i++) {
			// Load <srcAttr/> List
			List<TuringTag> turingTagsPerSrcAttr = loadAtributesFromAttrsElement((Element) attributes.item(i));
			turingTagMap.addAll(turingTagsPerSrcAttr);
		}

		if (log.isDebugEnabled()) {
			log.debug(String.format("%s Attributes", genericIndexAttrsTag));
			for (TuringTag turingTag : turingTagMap)
				log.debug(String.format(" Tag %s - Attribute %s", turingTag.getTagName(), turingTag.getSrcXmlName()));
		}
		return turingTagMap;
	}

	// Load <srcAttr/> List
	public static List<TuringTag> loadAtributesFromAttrsElement(Element attrsElement) {
		NodeList srcNodeList = attrsElement.getElementsByTagName("srcAttr");
		List<TuringTag> turingTagsPerSrcAttr = new ArrayList<>();

		for (int i = 0; i < srcNodeList.getLength(); i++) {
			Element srcAttrNode = (Element) srcNodeList.item(i);
			if (srcAttrNode.hasAttributes() && (srcAttrNode.hasAttribute(TurXMLConstant.XML_NAME_ATT)
					|| srcAttrNode.hasAttribute(TurXMLConstant.CLASS_NAME_ATT)
					|| srcAttrNode.hasAttribute(TurXMLConstant.TEXT_VALUE_ATT))) {
				List<TuringTag> turingTags = loadSrcAttr(srcAttrNode);
				if (turingTags != null)
					turingTagsPerSrcAttr.addAll(turingTags);
			}
		}
		return turingTagsPerSrcAttr;
	}

	// Read <srcAttr/>
	public static List<TuringTag> loadSrcAttr(Element srcAttrNode) {
		TuringTag turingTagCheck = detectXMLAttributesOfSrcAttr(srcAttrNode);

		if ((turingTagCheck.getSrcXmlName() != null) || (turingTagCheck.getSrcClassName() != null)
				|| (turingTagCheck.getTextValue() != null)) {
			ArrayList<TuringTag> turingTags = readTagList(srcAttrNode);
			if (!turingTags.isEmpty()) {
				return turingTags;
			}
		}
		return Collections.emptyList();
	}

	private static TuringTag detectXMLAttributesOfSrcAttr(Element srcAttrNode) {
		TuringTag turingTag = new TuringTag();
		if (srcAttrNode.hasAttribute(TurXMLConstant.XML_NAME_ATT))
			turingTag.setSrcXmlName(srcAttrNode.getAttribute(TurXMLConstant.XML_NAME_ATT));

		if (srcAttrNode.hasAttribute(TurXMLConstant.CLASS_NAME_ATT))
			turingTag.setSrcClassName(srcAttrNode.getAttribute(TurXMLConstant.CLASS_NAME_ATT));

		if (srcAttrNode.hasAttribute(TurXMLConstant.VALUE_TYPE_ATT))
			turingTag.setSrcAttributeType(srcAttrNode.getAttribute(TurXMLConstant.VALUE_TYPE_ATT));

		if (srcAttrNode.hasAttribute(TurXMLConstant.RELATION_ATT))
			turingTag.setSrcAttributeRelation(
					Arrays.asList(srcAttrNode.getAttribute(TurXMLConstant.RELATION_ATT).split("\\.")));

		if (srcAttrNode.hasAttribute(TurXMLConstant.TEXT_VALUE_ATT)) {
			turingTag.setTextValue(srcAttrNode.getAttribute(TurXMLConstant.TEXT_VALUE_ATT));
		}
		if (srcAttrNode.hasAttribute(TurXMLConstant.MANDATORY_ATT)) {
			if (log.isDebugEnabled())
				log.debug(String.format("MANDATORY: %s", srcAttrNode.getAttribute(TurXMLConstant.MANDATORY_ATT)));

			turingTag.setSrcMandatory(Boolean.parseBoolean(srcAttrNode.getAttribute(TurXMLConstant.MANDATORY_ATT)));

		} else
			turingTag.setSrcMandatory(false);
		if (log.isDebugEnabled())
			log.debug(String.format("Mandatory: %b", turingTag.getSrcMandatory()));

		readUniqueValuesAttr(srcAttrNode, turingTag);

		return turingTag;
	}

	private static ArrayList<TuringTag> readTagList(Element srcAttrNode) {

		NodeList tagList = (srcAttrNode).getElementsByTagName("tag");

		ArrayList<TuringTag> turingTags = new ArrayList<>();

		for (int nodePos = 0; nodePos < tagList.getLength(); nodePos++) {
			TuringTag turingTag = detectXMLAttributesOfSrcAttr(srcAttrNode);
			if (log.isDebugEnabled()) {
				log.debug("Node Parent: " + turingTag.getSrcXmlName());
				log.debug("Node.getLength(): " + tagList.getLength());
			}

			if (log.isDebugEnabled())
				log.debug("Node: " + nodePos);

			String tagName = null;
			Node tagNode = tagList.item(nodePos);
			tagName = tagNode.getFirstChild().getNodeValue();
			if (log.isDebugEnabled())
				log.debug("tagName:" + tagName);

			if (tagName != null) {
				turingTag.setTagName(tagName);
				turingTags.add(turingTag);

			}
		}
		return turingTags;
	}

	private static void readUniqueValuesAttr(Element srcAttrNode, TuringTag turingTag) {
		if (srcAttrNode.hasAttribute(TurXMLConstant.UNIQUE_VALUES_ATT))
			turingTag.setSrcUniqueValues(
					Boolean.parseBoolean(srcAttrNode.getAttribute(TurXMLConstant.UNIQUE_VALUES_ATT)));
		else
			turingTag.setSrcUniqueValues(false);
		if (log.isDebugEnabled())
			log.debug(String.format("Unique Values: %b", turingTag.isSrcUniqueValues()));

	}

	public static MappingDefinitions getMappingDefinitions(IHandlerConfiguration config) {

		MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.loadMappings(config.getMappingsXML());

		if (mappingDefinitions == null && log.isDebugEnabled())
			log.error("Mapping definitions are not loaded properly from mappingsXML: " + config.getMappingsXML());

		return mappingDefinitions;
	}

}
