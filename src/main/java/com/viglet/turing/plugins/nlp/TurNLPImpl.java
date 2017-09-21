package com.viglet.turing.plugins.nlp;

import javax.xml.transform.TransformerException;

import com.viglet.turing.nlp.TurNLPResults;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;

public interface TurNLPImpl {
	public TurNLPResults retrieve(String text) throws TransformerException, Exception;

	public void startup(TurNLPInstance turNLPInstance);
}
