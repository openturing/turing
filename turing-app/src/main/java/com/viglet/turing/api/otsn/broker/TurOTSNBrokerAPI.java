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
package com.viglet.turing.api.otsn.broker;

import com.google.inject.Inject;
import com.viglet.turing.api.sn.job.*;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.*;
@Slf4j
@RestController
@RequestMapping("/api/otsn/broker")
@Tag(name = "OTSN Broker", description = "OTSN Broker API")
public class TurOTSNBrokerAPI {
	public static final String FAILED = "Failed";
	public static final String OK = "Ok";
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurSNImportAPI turSNImportAPI;
	@Inject
	public TurOTSNBrokerAPI(TurSNSiteRepository turSNSiteRepository, TurSNImportAPI turSNImportAPI) {
		this.turSNSiteRepository = turSNSiteRepository;
		this.turSNImportAPI = turSNImportAPI;
	}

	@PostMapping
	public String turOTSNBrokerAdd(@RequestParam("index") String siteName, @RequestParam("config") String config,
			@RequestParam("data") String data) {
		return indexItem(getFirstSiteName(siteName), readXML(data));

	}

	private String indexItem(String siteName, Document document) {
		return turSNSiteRepository.findByName(siteName).map(turSNSite -> {
			if (document != null) {
				Element element = document.getDocumentElement();
				NodeList nodes = element.getChildNodes();
				TurSNJobItem turSNJobItem = createJobItem(nodes);
				TurSNJobItems turSNJobItems = new TurSNJobItems();
				turSNJobItems.add(turSNJobItem);
				TurSNJob turSNJob = createSNJob(turSNSite, turSNJobItems);
				log.debug("Indexed Job by Id");
				turSNImportAPI.send(turSNJob);
				return OK;
			} else {
				return FAILED;
			}
		}).orElse(FAILED);

	}

	private TurSNJob createSNJob(TurSNSite turSNSite, TurSNJobItems turSNJobItems) {
		TurSNJob turSNJob = new TurSNJob();
		turSNJob.setSiteId(turSNSite.getId());

		turSNJob.setTurSNJobItems(turSNJobItems);
		return turSNJob;
	}

	private TurSNJobItem createJobItem(NodeList nodes) {
		TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE);
		Map<String, Object> attributes = addAttributesToJobItem(nodes, turSNJobItem);
		turSNJobItem.setAttributes(attributes);
		return turSNJobItem;
	}

	private Map<String, Object> addAttributesToJobItem(NodeList nodes, TurSNJobItem turSNJobItem) {
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
			log.error(e.getMessage(), e);
		}
		DocumentBuilder builder;
		Document document = null;
		try {
			builder = factory.newDocumentBuilder();
			document = builder.parse(new InputSource(new StringReader(data)));
		} catch (Exception e) {
			log.error(e.getMessage(), e);
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
			TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.DELETE);
			siteName = getFirstSiteName(siteName);

			Map<String, Object> attributes = new HashMap<>();
			if (id.isPresent()) {
				log.debug("DeIndexed Job by Id");
				attributes.put("id", id.get());
			} else if (type.isPresent()) {
				log.debug("DeIndexed Job by Type");
				attributes.put("type", type.get());
			}
			turSNJobItem.setAttributes(attributes);
			turSNJobItems.add(turSNJobItem);
			TurSNJob turSNJob = new TurSNJob();
			turSNSiteRepository.findByName(siteName)
					.ifPresent(turSNSite -> turSNJob.setSiteId(turSNSite.getId()));

			turSNJob.setTurSNJobItems(turSNJobItems);
			turSNImportAPI.send(turSNJob);
			return OK;

		} else {
			return FAILED;
		}
	}
}
