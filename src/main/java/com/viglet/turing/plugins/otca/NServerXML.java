package com.viglet.turing.plugins.otca;

import java.util.List;

import org.json.*;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.service.nlp.TurNLPInstanceEntityService;

public class NServerXML {
	List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	public static int PRETTY_PRINT_INDENT_FACTOR = 4;
	public JSONObject json;
	TurNLPInstance turNLPInstance = null;
	
	public NServerXML(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;

		TurNLPInstanceEntityService turNLPInstanceEntityService = new TurNLPInstanceEntityService();
		nlpInstanceEntities = turNLPInstanceEntityService.findByNLPInstance(turNLPInstance);
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
			jsonObject.put(entity.getTurNLPEntity().getCollectionName(), this.getEntity(entity.getName()));
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
