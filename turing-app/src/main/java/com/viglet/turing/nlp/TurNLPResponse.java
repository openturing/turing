package com.viglet.turing.nlp;

import java.util.List;
import java.util.Map;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

public class TurNLPResponse {
	private TurNLPInstance turNLPInstance;
	private TurNLPVendor turNLPVendor;
	private List<TurNLPVendorEntity> turNLPVendorEntities;
	private Map<String, List<String>> entityMapWithProcessedValues;

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

	public List<TurNLPVendorEntity> getTurNLPVendorEntities() {
		return turNLPVendorEntities;
	}

	public void setTurNLPVendorEntities(List<TurNLPVendorEntity> turNLPVendorEntities) {
		this.turNLPVendorEntities = turNLPVendorEntities;
	}

}
