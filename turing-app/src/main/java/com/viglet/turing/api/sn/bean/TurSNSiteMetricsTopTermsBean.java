package com.viglet.turing.api.sn.bean;

import java.util.List;

import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

public class TurSNSiteMetricsTopTermsBean {

	private List<TurSNSiteMetricAccessTerm> topTerms;

	private int totalTermsPeriod;

	private int totalTermsPreviousPeriod;

	private int variationPeriod;

	public TurSNSiteMetricsTopTermsBean(List<TurSNSiteMetricAccessTerm> metricsTerms, int totalTermsPeriod,
			int totalTermsPreviousPeriod) {
		super();
		this.topTerms = metricsTerms;
		this.totalTermsPeriod = totalTermsPeriod;
		this.totalTermsPreviousPeriod = totalTermsPreviousPeriod;
		if (totalTermsPreviousPeriod == 0) {
			this.variationPeriod = 0;
		} else {
			float total = ((float) totalTermsPeriod / (float) totalTermsPreviousPeriod);
			this.variationPeriod = (int) ((total < 1) ? (-1) * (1 - total) * 100 : (total * 100) - 100);
		}
	}

	public int getTotalTermsPeriod() {
		return totalTermsPeriod;
	}

	public void setTotalTermsPeriod(int totalTermsPeriod) {
		this.totalTermsPeriod = totalTermsPeriod;
	}

	public List<TurSNSiteMetricAccessTerm> getTopTerms() {
		return topTerms;
	}

	public void setTopTerms(List<TurSNSiteMetricAccessTerm> topTerms) {
		this.topTerms = topTerms;
	}

	public int getTotalTermsPreviousPeriod() {
		return totalTermsPreviousPeriod;
	}

	public void setTotalTermsPreviousPeriod(int totalTermsPreviousPeriod) {
		this.totalTermsPreviousPeriod = totalTermsPreviousPeriod;
	}

	public int getVariationPeriod() {
		return variationPeriod;
	}

	public void setVariationPeriod(int variationPeriod) {
		this.variationPeriod = variationPeriod;
	}

}
