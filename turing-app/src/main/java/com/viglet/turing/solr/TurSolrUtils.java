package com.viglet.turing.solr;

import org.apache.solr.common.SolrDocument;

import com.viglet.turing.se.TurSEParameters;
import com.viglet.turing.se.result.TurSEResult;

public class TurSolrUtils {

    private TurSolrUtils() {
        throw new IllegalStateException("Solr Utility class");
    }

    public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
        TurSEResult turSEResult = new TurSEResult();
        document.getFieldNames()
                .forEach(attribute -> turSEResult.getFields().put(attribute, document.getFieldValue(attribute)));
        return turSEResult;
    }
    
    public static int firstRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
		return (turSEParameters.getCurrentPage() * turSEParameters.getRows()) - turSEParameters.getRows();
	}

	public static int lastRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
		return (turSEParameters.getCurrentPage() * turSEParameters.getRows());
	}
}
