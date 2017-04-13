package com.viglet.turing.plugins.nlp;

import javax.xml.transform.TransformerException;

import org.json.JSONObject;

import com.viglet.turing.nlp.VigNLPResults;
import com.viglet.turing.persistence.model.VigService;

public interface NLPImpl {
	public VigNLPResults retrieve(String text) throws TransformerException, Exception;
}
