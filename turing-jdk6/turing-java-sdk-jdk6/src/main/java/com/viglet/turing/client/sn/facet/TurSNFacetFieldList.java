package com.viglet.turing.client.sn.facet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.viglet.turing.api.sn.bean.TurSNSiteSearchFacetBean;

public class TurSNFacetFieldList implements Iterable<TurSNFacetField> {

	private List<TurSNFacetField> turSNFacetFields = new ArrayList<TurSNFacetField>();

	private TurSNFacetField turSNFacetToRemove = null;

	public TurSNFacetFieldList(List<TurSNSiteSearchFacetBean> facets, TurSNSiteSearchFacetBean facetToRemove) {

		if (facetToRemove != null) {
			
			TurSNFacetFieldValueList turSNFacetToRemoveFieldValues = new TurSNFacetFieldValueList(
					facetToRemove.getFacets());
			turSNFacetToRemove = new TurSNFacetField();
			turSNFacetToRemove.setValues(turSNFacetToRemoveFieldValues);
			turSNFacetToRemove.setLabel(facetToRemove.getLabel().getText());
		}
		
		if (facets != null) {
			for (TurSNSiteSearchFacetBean facet : facets) {
				TurSNFacetFieldValueList turSNFacetFieldValues = new TurSNFacetFieldValueList(facet.getFacets());
				TurSNFacetField turSNFacetField = new TurSNFacetField();
				turSNFacetField.setLabel(facet.getLabel().getText());
				turSNFacetField.setName(facet.getName());
				turSNFacetField.setDescription(facet.getDescription());
				turSNFacetField.setMultiValued(facet.isMultiValued());
				turSNFacetField.setType(facet.getType());
				turSNFacetField.setValues(turSNFacetFieldValues);
				this.turSNFacetFields.add(turSNFacetField);
			}
		}
	}

	@Override
	public Iterator<TurSNFacetField> iterator() {
		return turSNFacetFields.iterator();
	}

	public List<TurSNFacetField> getFields() {
		return turSNFacetFields;
	}

	public TurSNFacetField getFacetWithRemovedValues() {
		return turSNFacetToRemove;
	}
}
