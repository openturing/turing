package com.viglet.turing.nlp;

import java.util.List;
import java.util.Map;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;

public class TurNLP {
	private TurNLPInstance turNLPInstance;
	private TurNLPVendor turNLPVendor;
	private List<TurNLPInstanceEntity> nlpInstanceEntities;
	private Map<String, List<String>> entityMapWithProcessedValues;
	private Map<String, Object> attributeMapToBeProcessed;

	public TurNLPInstance getTurNLPInstance() {
		return turNLPInstance;
	}

	public void setTurNLPInstance(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
	}

	public TurNLPVendor getTurNLPVendor() {
		return turNLPVendor;
	}

	public void setTurNLPVendor(TurNLPVendor turNLPVendor) {
		this.turNLPVendor = turNLPVendor;
	}

	
	public Map<String, List<String>> getEntityMapWithProcessedValues() {
		return entityMapWithProcessedValues;
	}

	public void setEntityMapWithProcessedValues(Map<String, List<String>> entityMapWithProcessedValues) {
		this.entityMapWithProcessedValues = entityMapWithProcessedValues;
	}

	public Map<String, Object> getAttributeMapToBeProcessed() {
		return attributeMapToBeProcessed;
	}

	public void setAttributeMapToBeProcessed(Map<String, Object> attributeMapToBeProcessed) {
		this.attributeMapToBeProcessed = attributeMapToBeProcessed;
	}

	public List<TurNLPInstanceEntity> getNlpInstanceEntities() {
		return nlpInstanceEntities;
	}

	public void setNlpInstanceEntities(List<TurNLPInstanceEntity> nlpInstanceEntities) {
		this.nlpInstanceEntities = nlpInstanceEntities;
	}

	

}
