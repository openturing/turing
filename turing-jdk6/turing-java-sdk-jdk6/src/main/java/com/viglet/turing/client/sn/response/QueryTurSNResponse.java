/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn.response;

import java.util.List;

import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.didyoumean.TurSNDidYouMean;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.spotlight.TurSNSpotlightDocument;

/**
 * Return results of Turing AI response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */
public class QueryTurSNResponse {

	private TurSNDocumentList results;

	private TurSNPagination pagination;

	private TurSNDidYouMean didYouMean;

	private TurSNFacetFieldList facetFields;

	private List<TurSNSpotlightDocument> spotlightDocuments;
	
	public TurSNDocumentList getResults() {
		return results;
	}

	public TurSNPagination getPagination() {
		return pagination;
	}

	public void setResults(TurSNDocumentList results) {
		this.results = results;
	}

	public void setPagination(TurSNPagination pagination) {
		this.pagination = pagination;
	}

	public TurSNFacetFieldList getFacetFields() {
		return facetFields;
	}

	public void setFacetFields(TurSNFacetFieldList facetFields) {
		this.facetFields = facetFields;
	}

	public TurSNDidYouMean getDidYouMean() {
		return didYouMean;
	}

	public void setDidYouMean(TurSNDidYouMean didYouMean) {
		this.didYouMean = didYouMean;
	}

	public List<TurSNSpotlightDocument> getSpotlightDocuments() {
		return spotlightDocuments;
	}

	public void setSpotlightDocuments(List<TurSNSpotlightDocument> spotlightDocuments) {
		this.spotlightDocuments = spotlightDocuments;
	}

}
