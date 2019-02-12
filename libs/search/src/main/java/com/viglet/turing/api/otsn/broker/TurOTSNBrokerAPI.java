package com.viglet.turing.api.otsn.broker;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.InputSource;

import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.api.sn.job.TurSNJobItems;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.solr.TurSolr;

import io.swagger.annotations.Api;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@RestController
@RequestMapping("/api/otsn/broker")
@Api(tags = "OTSN Broker", description = "OTSN Broker API")
public class TurOTSNBrokerAPI {
	static final Logger logger = LogManager.getLogger(TurOTSNBrokerAPI.class.getName());
	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	@Autowired
	TurSolr turSolr;
	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	public static final String INDEXING_QUEUE = "indexing.queue";

	@PostMapping
	public String turOTSNBrokerAdd(@RequestParam("index") String siteName, @RequestParam("config") String config,
			@RequestParam("data") String data) {
		if (siteName.contains(",")) {
			String[] siteNames = siteName.split(",");
			siteName = siteNames[0];
		}

		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Document document = null;
		try {
			builder = factory.newDocumentBuilder();
			document = builder.parse(new InputSource(new StringReader(data)));
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (document != null) {
			Element element = document.getDocumentElement();

			NodeList nodes = element.getChildNodes();

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
			turSNJobItem.setAttributes(attributes);

			TurSNJobItems turSNJobItems = new TurSNJobItems();

			turSNJobItems.add(turSNJobItem);

			TurSNJob turSNJob = new TurSNJob();
			turSNJob.setSiteId(turSNSite.getId());

			turSNJob.setTurSNJobItems(turSNJobItems);
			logger.debug("Indexed Job by Id");
			sendIndexerJob(turSNJob);

			return "Ok";
		} else {
			return "Failed";
		}

	}

	@GetMapping
	public String turOTSNBrokerDelete(@RequestParam("index") String siteName, @RequestParam("config") String config,
			@RequestParam("action") String action, @RequestParam("id") Optional<String> id,
			@RequestParam("type") Optional<String> type) {

		if (action.equals("delete")) {
			TurSNJobItems turSNJobItems = new TurSNJobItems();
			TurSNJobItem turSNJobItem = new TurSNJobItem();
			turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);

			if (siteName.contains(",")) {
				String[] siteNames = siteName.split(",");
				siteName = siteNames[0];
			}
			TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
			Map<String, Object> attributes = new HashMap<String, Object>();
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
			sendIndexerJob(turSNJob);
			return "Ok";

		} else {
			return "Failed";
		}
	}

	public void sendIndexerJob(TurSNJob turSNJob) {
		logger.debug("Sent Indexer Job - " + INDEXING_QUEUE);
		this.jmsMessagingTemplate.convertAndSend(INDEXING_QUEUE, turSNJob);

	}
}
