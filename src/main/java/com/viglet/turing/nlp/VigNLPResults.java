package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.viglet.turing.persistence.model.TurNLPEntity;

public class VigNLPResults {
	
	List<TurNLPEntity> turNLPEntities = new ArrayList<TurNLPEntity> (); 
	JSONObject jsonResult = new JSONObject();
	JSONObject jsonAttributes = new JSONObject();
	
	public List<TurNLPEntity> getTurNLPEntities() {
		return turNLPEntities;
	}
	public JSONObject getJsonAttributes() {
		return jsonAttributes;
	}
	public void setJsonAttributes(JSONObject jsonAttributes) {
		this.jsonAttributes = jsonAttributes;
	}
	public void setTurNLPEntities(List<TurNLPEntity> turNLPEntities) {
		this.turNLPEntities = turNLPEntities;
	}
	public JSONObject getJsonResult() {
		return jsonResult;
	}
	public void setJsonResult(JSONObject jsonResult) {
		this.jsonResult = jsonResult;
	}

}
