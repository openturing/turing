package com.viglet.turing.api.nlp;

import java.util.ArrayList;
import java.util.List;

public class TurNLPValidateResponse {

	private String vendor;
	
	private String locale;
	
	private List<TurNLPEntityValidateResponse> entities = new ArrayList<TurNLPEntityValidateResponse>();

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public List<TurNLPEntityValidateResponse> getEntities() {
		return entities;
	}

	public void setEntities(List<TurNLPEntityValidateResponse> entities) {
		this.entities = entities;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}
	
	
}
