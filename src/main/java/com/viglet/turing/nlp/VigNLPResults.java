package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import com.viglet.turing.persistence.model.VigServicesNLPEntity;

public class VigNLPResults {
	
	List<VigServicesNLPEntity> vigNLPServicesEntity = new ArrayList<VigServicesNLPEntity> (); 
	JSONObject jsonResult = new JSONObject();
	JSONObject jsonAttributes = new JSONObject();
	
	public List<VigServicesNLPEntity> getVigNLPServicesEntity() {
		return vigNLPServicesEntity;
	}
	public JSONObject getJsonAttributes() {
		return jsonAttributes;
	}
	public void setJsonAttributes(JSONObject jsonAttributes) {
		this.jsonAttributes = jsonAttributes;
	}
	public void setVigNLPServicesEntity(List<VigServicesNLPEntity> vigNLPServicesEntity) {
		this.vigNLPServicesEntity = vigNLPServicesEntity;
	}
	public JSONObject getJsonResult() {
		return jsonResult;
	}
	public void setJsonResult(JSONObject jsonResult) {
		this.jsonResult = jsonResult;
	}

}
