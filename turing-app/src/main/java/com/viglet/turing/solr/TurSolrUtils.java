package com.viglet.turing.solr;

import org.apache.solr.common.SolrDocument;

import com.viglet.turing.se.result.TurSEResult;

public class TurSolrUtils {

	public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
		TurSEResult turSEResult = new TurSEResult();
		document.getFieldNames()
				.forEach(attribute -> turSEResult.getFields().put(attribute, document.getFieldValue(attribute)));
		return turSEResult;
	}
}
