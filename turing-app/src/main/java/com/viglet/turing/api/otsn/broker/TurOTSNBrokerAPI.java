/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.api.otsn.broker;

import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.InputSource;

import com.viglet.turing.api.sn.job.TurSNImportAPI;
import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.api.sn.job.TurSNJobItems;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@RestController
@RequestMapping("/api/otsn/broker")
@Tag(name = "OTSN Broker", description = "OTSN Broker API")
public class TurOTSNBrokerAPI {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNImportAPI turSNImportAPI;

	@PostMapping
	public String turOTSNBrokerAdd(@RequestParam("index") String siteName, @RequestParam("config") String config,
			@RequestParam("data") String data) {
		siteName = getFirstSiteName(siteName);

		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		Document document = readXML(data);

		return indexItem(turSNSite, document);

	}

	private String indexItem(TurSNSite turSNSite, Document document) {
		if (document != null) {
			Element element = document.getDocumentElement();

			NodeList nodes = element.getChildNodes();

			TurSNJobItem turSNJobItem = createJobItem(nodes);

			TurSNJobItems turSNJobItems = new TurSNJobItems();

			turSNJobItems.add(turSNJobItem);

			TurSNJob turSNJob = createSNJob(turSNSite, turSNJobItems);
			logger.debug("Indexed Job by Id");
			turSNImportAPI.send(turSNJob);

			return "Ok";
		} else {
			return "Failed";
		}
	}

	private TurSNJob createSNJob(TurSNSite turSNSite, TurSNJobItems turSNJobItems) {
		TurSNJob turSNJob = new TurSNJob();
		turSNJob.setSiteId(turSNSite.getId());

		turSNJob.setTurSNJobItems(turSNJobItems);
		return turSNJob;
	}

	private TurSNJobItem createJobItem(NodeList nodes) {
		TurSNJobItem turSNJobItem = new TurSNJobItem();
		Map<String, Object> attributes = addAtributesToJobItem(nodes, turSNJobItem);
		turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
		turSNJobItem.setAttributes(attributes);
		return turSNJobItem;
	}

	private Map<String, Object> addAtributesToJobItem(NodeList nodes, TurSNJobItem turSNJobItem) {
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
		return attributes;
	}

	private Document readXML(String data) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (ParserConfigurationException e) {
			logger.error(e.getMessage(), e);
		}
		DocumentBuilder builder;
		Document document = null;
		try {
			builder = factory.newDocumentBuilder();
			document = builder.parse(new InputSource(new StringReader(data)));
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return document;
	}

	private String getFirstSiteName(String siteName) {
		if (siteName.contains(",")) {
			String[] siteNames = siteName.split(",");
			siteName = siteNames[0];
		}
		return siteName;
	}

	@GetMapping
	public String turOTSNBrokerDelete(@RequestParam("index") String siteName, @RequestParam("config") String config,
			@RequestParam("action") String action, @RequestParam("id") Optional<String> id,
			@RequestParam("type") Optional<String> type) {

		if (action.equals("delete")) {
			TurSNJobItems turSNJobItems = new TurSNJobItems();
			TurSNJobItem turSNJobItem = new TurSNJobItem();
			turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);

			siteName = getFirstSiteName(siteName);
			TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
			Map<String, Object> attributes = new HashMap<>();
			if (id.isPresent()) {
				logger.debug("Deindexed Job by Id");
				attributes.put("id", id.get());
			} else if (type.isPresent()) {
				logger.debug("Deindexed Job by Type");
				attributes.put("type", type.get());
			}
			turSNJobItem.setAttributes(attributes);
			turSNJobItems.add(turSNJobItem);
			TurSNJob turSNJob = new TurSNJob();
			turSNJob.setSiteId(turSNSite.getId());
			turSNJob.setTurSNJobItems(turSNJobItems);
			turSNImportAPI.send(turSNJob);
			return "Ok";

		} else {
			return "Failed";
		}
	}
}
