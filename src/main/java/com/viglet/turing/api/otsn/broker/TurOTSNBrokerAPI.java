package com.viglet.turing.api.otsn.broker;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.InputSource;

import com.viglet.turing.api.sn.TurSNJob;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
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
	private JmsMessagingTemplate jmsMessagingTemplate;

	public static final String INDEXING_QUEUE = "indexing.queue";
	public static final String NLP_QUEUE = "nlp.queue";
	public static final String DEINDEXING_QUEUE = "deindexing.queue";

	@PostMapping
	public String turOTSNBrokerAdd(@RequestParam("index") String index, @RequestParam("config") String config,
			@RequestParam("data") String data) throws JSONException {

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Document document = null;
		try {
			builder = factory.newDocumentBuilder();
			document = builder.parse(new InputSource(new StringReader(data)));
		} catch (Exception e) {
			e.printStackTrace();
		}
		Element element = document.getDocumentElement();
		JSONArray items = new JSONArray();
		JSONObject attributes = new JSONObject();

		NodeList nodes = element.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			String nodeName = nodes.item(i).getNodeName();
			if (attributes.has(nodeName)) {
				if (!(attributes.get(nodeName) instanceof JSONArray)) {
					JSONArray attributeValues = new JSONArray();
					attributeValues.put(attributes.get(nodeName));
					attributeValues.put(nodes.item(i).getTextContent());
					attributes.put(nodeName, attributeValues);
				} else {
					attributes.getJSONArray(nodeName).put(nodes.item(i).getTextContent());
				}
			} else {
				attributes.put(nodeName, nodes.item(i).getTextContent());

			}
		}

		items.put(attributes);
		TurSNJob turSNJob = new TurSNJob();
		turSNJob.setSiteId("1");
		System.out.println(items.toString());
		turSNJob.setJson(items.toString());
		index(turSNJob);

		return "Ok";

	}

	@GetMapping
	public String turOTSNBrokerDelete(@RequestParam("index") String index, @RequestParam("config") String config,
			@RequestParam("action") String action, @RequestParam("id") String id) throws JSONException {

		if (action.equals("delete")) {
			JSONArray json = new JSONArray();
			JSONObject objectId = new JSONObject();
			objectId.put("id", id);
			json.put(objectId);

			TurSNJob turSNJob = new TurSNJob();
			turSNJob.setSiteId(index);
			turSNJob.setJson(json.toString());
			deindex(turSNJob);
			return "Ok";

		} else {
			return "Failed";
		}
	}

	public void index(TurSNJob turSNJob) {
		logger.debug("Sent Index job - " + INDEXING_QUEUE);
		this.jmsMessagingTemplate.convertAndSend(INDEXING_QUEUE, turSNJob);

	}

	public void deindex(TurSNJob turSNJob) {
		logger.debug("Sent Deindex job - " + DEINDEXING_QUEUE);
		this.jmsMessagingTemplate.convertAndSend(DEINDEXING_QUEUE, turSNJob);

	}
}
