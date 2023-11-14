package com.viglet.turing.client.sn.facet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.viglet.turing.commons.sn.bean.TurSNSiteSearchFacetItemBean;

public class TurSNFacetFieldValueList implements Iterable<TurSNFacetFieldValue>{
	private List<TurSNFacetFieldValue> turFacetFieldValues = new ArrayList<>();
	public TurSNFacetFieldValueList(List<TurSNSiteSearchFacetItemBean> facetItems) {
		for (TurSNSiteSearchFacetItemBean facetItem : facetItems) {
			TurSNFacetFieldValue turSNFacetFieldValue = new TurSNFacetFieldValue();
			turSNFacetFieldValue.setLabel(facetItem.getLabel());
			turSNFacetFieldValue.setApiURL(facetItem.getLink());
			turSNFacetFieldValue.setCount(facetItem.getCount());
			turFacetFieldValues.add(turSNFacetFieldValue);
		}
	}

	@Override
	public Iterator<TurSNFacetFieldValue> iterator() {
		return turFacetFieldValues.iterator();
	}
	
	public List<TurSNFacetFieldValue> getTurSNFacetFieldValues() {
		return turFacetFieldValues;
	}

}
