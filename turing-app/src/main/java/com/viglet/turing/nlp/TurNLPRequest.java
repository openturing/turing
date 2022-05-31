package com.viglet.turing.nlp;

import java.util.List;
import java.util.Map;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

public class TurNLPRequest {
	private TurNLPInstance turNLPInstance;
	private Map<String, Object> data;
	private List<TurNLPVendorEntity> entities;
	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}
	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}
	public Map<String, Object> getData() {
		return data;
	}
	public void setData(Map<String, Object> data) {
		this.data = data;
	}
	public List<TurNLPVendorEntity> getEntities() {
		return entities;
	}
	public void setEntities(List<TurNLPVendorEntity> list) {
		this.entities = list;
	}


}
