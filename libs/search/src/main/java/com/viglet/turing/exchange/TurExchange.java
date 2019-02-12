package com.viglet.turing.exchange;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.turing.exchange.sn.TurSNSiteExchange;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurExchange {

	@JsonInclude(Include.NON_NULL)
	private List<TurSNSiteExchange> snSites;

	public List<TurSNSiteExchange> getSnSites() {
		return snSites;
	}

	public void setSnSites(List<TurSNSiteExchange> snSites) {
		this.snSites = snSites;
	}



}
