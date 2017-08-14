package com.viglet.turing.plugins.nlp;

import javax.xml.transform.TransformerException;

import com.viglet.turing.nlp.VigNLPResults;

public interface NLPImpl {
	public VigNLPResults retrieve(String text) throws TransformerException, Exception;
}
