package com.viglet.turing.api.nlp;

import java.util.List;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;

public class TurNLPEntityValidateResponse {

	private TurNLPEntity type;
	
	private List<Object> terms;

	public TurNLPEntity getType() {
		return type;
	}

	public void setType(TurNLPEntity type) {
		this.type = type;
	}

	public List<Object> getTerms() {
		return terms;
	}

	public void setTerms(List<Object> terms) {
		this.terms = terms;
	}

	
}
