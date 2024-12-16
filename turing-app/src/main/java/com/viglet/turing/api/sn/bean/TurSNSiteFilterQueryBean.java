package com.viglet.turing.api.sn.bean;

import java.util.List;

public class TurSNSiteFilterQueryBean {

	private List<String> hiddenItems;

	private List<String> items;

	public List<String> getHiddenItems() {
		return hiddenItems;
	}

	public void setHiddenItems(List<String> hiddenItems) {
		this.hiddenItems = hiddenItems;
	}

	public List<String> getItems() {
		return items;
	}

	public void setItems(List<String> items) {
		this.items = items;
	}

}
