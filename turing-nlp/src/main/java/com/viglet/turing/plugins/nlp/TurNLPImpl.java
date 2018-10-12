package com.viglet.turing.plugins.nlp;

import java.util.Map;

import javax.xml.transform.TransformerException;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;

public interface TurNLPImpl {
	public Map<String, Object> retrieve(Map<String, Object> attributes) throws TransformerException, Exception;

	public void startup(TurNLPInstance turNLPInstance);
}
