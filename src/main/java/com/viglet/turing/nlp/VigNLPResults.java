package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.viglet.turing.persistence.model.TurNLPInstanceEntity;

public class VigNLPResults {
	
	List<TurNLPInstanceEntity> turNLPInstanceEntities = new ArrayList<TurNLPInstanceEntity> (); 
	JSONObject jsonResult = new JSONObject();
	JSONObject jsonAttributes = new JSONObject();
	
	public List<TurNLPInstanceEntity> getTurNLPInstanceEntities() {
		return turNLPInstanceEntities;
	}
	public JSONObject getJsonAttributes() {
		return jsonAttributes;
	}
	public void setJsonAttributes(JSONObject jsonAttributes) {
		this.jsonAttributes = jsonAttributes;
	}
	public void setTurNLPInstanceEntities(List<TurNLPInstanceEntity> turNLPInstanceEntities) {
		this.turNLPInstanceEntities = turNLPInstanceEntities;
	}
	public JSONObject getJsonResult() {
		return jsonResult;
	}
	public void setJsonResult(JSONObject jsonResult) {
		this.jsonResult = jsonResult;
	}

}
