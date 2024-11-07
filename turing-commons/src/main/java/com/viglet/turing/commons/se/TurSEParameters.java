package com.viglet.turing.commons.se;

import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
@Getter
@Setter
public class TurSEParameters  implements Serializable {
	private String query;
	private TurSEFilterQueryParameters filterQueries;
	private List<String> boostQueries;
	private Integer currentPage;
	private String sort;
	private Integer rows;
	private String group;
	private Integer autoCorrectionDisabled;
	private TurSNSitePostParamsBean turSNSitePostParamsBean;
	public TurSEParameters(String query, TurSEFilterQueryParameters filterQueries,
						   Integer currentPage, String sort, Integer rows,
						   String group, Integer autoCorrectionDisabled) {
		super();
		this.query = query;
		this.filterQueries = filterQueries;
		this.currentPage = currentPage;
		this.sort = sort;
		this.rows = rows;
		this.group = group;
		this.autoCorrectionDisabled = autoCorrectionDisabled;
		this.turSNSitePostParamsBean = null;
	}
	public TurSEParameters(String query, TurSEFilterQueryParameters filterQueries,
                           Integer currentPage, String sort, Integer rows,
                           String group, Integer autoCorrectionDisabled, TurSNSitePostParamsBean turSNSitePostParamsBean) {
		super();
		this.query = query;
		this.filterQueries = filterQueries;
		this.currentPage = currentPage;
		this.sort = sort;
		this.rows = rows;
		this.group = group;
		this.autoCorrectionDisabled = autoCorrectionDisabled;
		this.turSNSitePostParamsBean = turSNSitePostParamsBean;
		if (turSNSitePostParamsBean != null) {
			if (turSNSitePostParamsBean.getSort() != null) {
				this.sort = turSNSitePostParamsBean.getSort();
			}
			if (turSNSitePostParamsBean.getRows() != null) {
				this.rows = turSNSitePostParamsBean.getRows();
			}
			if (turSNSitePostParamsBean.getGroup() != null) {
				this.group = turSNSitePostParamsBean.getGroup();
			}
			if (turSNSitePostParamsBean.getPage() != null) {
				this.currentPage = turSNSitePostParamsBean.getPage();
			}
			if (turSNSitePostParamsBean.getQuery() != null) {
				this.query = turSNSitePostParamsBean.getQuery();
			}
			if (turSNSitePostParamsBean.getFq() != null) {
				this.filterQueries.setFq(turSNSitePostParamsBean.getFq());
			}
			if (turSNSitePostParamsBean.getFqAnd() != null) {
				this.filterQueries.setAnd(turSNSitePostParamsBean.getFqAnd());
			}
			if (turSNSitePostParamsBean.getFqOr() != null) {
				this.filterQueries.setOr(turSNSitePostParamsBean.getFqOr());
			}
			if (turSNSitePostParamsBean.getFqOperator() != null) {
				this.filterQueries.setOperator(turSNSitePostParamsBean.getFqOperator());
			}
		}
	}

	@Override
	public String toString() {
		return "TurSEParameters{" +
				"query='" + query + '\'' +
				", filterQueries=" + filterQueries +
				", boostQueries=" + boostQueries +
				", currentPage=" + currentPage +
				", sort='" + sort + '\'' +
				", rows=" + rows +
				", group='" + group + '\'' +
				", autoCorrectionDisabled=" + autoCorrectionDisabled +
				'}';
	}
}
