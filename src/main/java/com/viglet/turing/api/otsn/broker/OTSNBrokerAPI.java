package com.viglet.turing.api.otsn.broker;

import java.io.StringReader;

import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.InputSource;

import com.viglet.turing.persistence.service.nlp.TurNLPInstanceService;
import com.viglet.turing.persistence.service.se.TurSEInstanceService;
import com.viglet.turing.solr.TurSolr;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Path("/otsn/broker")
public class OTSNBrokerAPI {

	@POST
	@Produces("application/json")
	public Response broker(@FormParam("index") String index, @FormParam("config") String config,
			@FormParam("data") String data) throws JSONException {

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
		JSONObject jsonAttributes = new JSONObject();
		NodeList nodes = element.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			jsonAttributes.put(nodes.item(i).getNodeName(), nodes.item(i).getTextContent());

		}
		
		TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();
		TurSEInstanceService turSEInstanceService = new TurSEInstanceService();
		try {
			TurSolr turSolr = new TurSolr(turNLPInstanceService.getNLPDefault().getId(), turSEInstanceService.getSEDefault().getId(), jsonAttributes);
			turSolr.indexing();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(jsonAttributes.toString()).build();

	}
}
