package com.viglet.turing.plugins.otca;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.*;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.persistence.model.TurNLPInstanceEntity;

import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class NServerXML {
	List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	public static int PRETTY_PRINT_INDENT_FACTOR = 4;
	public JSONObject json;
	VigService vigService = null;
	
	@SuppressWarnings("unchecked")
	public NServerXML(VigService vigService) {
		this.vigService = vigService;
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryNLPEntity = em
				.createQuery(
						"SELECT sne FROM TurNLPInstanceEntity sne, VigService s where s.id = :id_service and sne.vigService = s and sne.enabled = :enabled ")
				.setParameter("id_service", vigService.getId()).setParameter("enabled", 1);

		nlpInstanceEntities = queryNLPEntity.getResultList();
	}
	public String toJSON(String xml) {
		String jsonResult = null;
		try {
			this.json = XML.toJSONObject(xml);

			String jsonPrettyPrintString = json.toString(PRETTY_PRINT_INDENT_FACTOR);
			jsonResult = jsonPrettyPrintString;
			System.out.println(jsonResult);
		} catch (JSONException je) {
			je.printStackTrace();
		}
		return jsonResult;
	}

	public JSONObject getJSON() {
		JSONObject jsonObject =  new JSONObject();;
		for (TurNLPInstanceEntity entity : nlpInstanceEntities) {
			jsonObject.put(entity.getTurEntity().getCollectionName(), this.getEntity(entity.getName()));
		}
		jsonObject.put("nlp","OTCA");
		return jsonObject;
	}
	public JSONArray getEntity(String entity) {
		JSONArray jsonEntity = new JSONArray();

		Object extractedTerm = this.json.getJSONObject("Nserver").getJSONObject("Results").getJSONObject("nfinder")
				.getJSONObject("nfExtract").get("ExtractedTerm");

		if (extractedTerm instanceof JSONArray) {
			JSONArray terms = (JSONArray) extractedTerm;
			for (int i = 0; i < terms.length(); i++) {
				JSONObject term = (JSONObject) terms.get(i);
				String ner = term.getString("CartridgeID");
				if (ner.equals(entity)) {
					String person = null;
					Object subterm = term.getJSONObject("Subterms").get("Subterm");
					if (subterm instanceof JSONArray) {
						person = ((JSONArray) subterm).getJSONObject(0).getString("content");
					} else {
						person = ((JSONObject) subterm).getString("content");
					}
					jsonEntity.put(person);
				}
			}
		} else {
			JSONObject term = (JSONObject) extractedTerm;
			String ner = term.getString("CartridgeID");
			if (ner.equals(entity)) {
				String person = null;
				Object subterm = term.getJSONObject("Subterms").get("Subterm");
				if (subterm instanceof JSONArray) {
					person = ((JSONArray) subterm).getJSONObject(0).getString("content");
				} else {
					person = ((JSONObject) subterm).getString("content");
				}				
				jsonEntity.put(person);
			}
		}
		return jsonEntity;
	}
	
}
