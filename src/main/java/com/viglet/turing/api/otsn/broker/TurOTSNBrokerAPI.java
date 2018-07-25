package com.viglet.turing.api.otsn.broker;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.xml.sax.InputSource;

import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.solr.TurSolr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@RestController
@RequestMapping("/api/otsn/broker")
public class TurOTSNBrokerAPI {
	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	@Autowired
	TurSolr turSolr;

	
	@PostMapping
	public Map<String, Object> broker(@RequestParam("index") String index, @RequestParam("config") String config,
			@RequestParam("data") String data) throws JSONException {

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("index", index);
		jsonObject.put("config", config);
		jsonObject.put("data", data);
		JSONObject turOTSNBrokerJSON = new JSONObject();
		turOTSNBrokerJSON.put("otsn-broker", jsonObject);

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
		Map<String, Object> attributes = new HashMap<String, Object>();
		NodeList nodes = element.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			attributes.put(nodes.item(i).getNodeName(), nodes.item(i).getTextContent());
		}

		try {
			turSolr.init();
			turSolr.setAttributes(attributes);
			turSolr.indexing();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return attributes;

	}
}
