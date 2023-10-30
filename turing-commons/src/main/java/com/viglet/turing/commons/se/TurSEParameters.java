package com.viglet.turing.commons.se;

import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
@Getter
@Setter
public class TurSEParameters {
	private String query;
	private List<String> filterQueries;
	private TurSNFilterQueryOperator fqOperator;
	private List<String> boostQueries;
	private Integer currentPage;
	private String sort;
	private Integer rows;
	private String group;
	private Integer autoCorrectionDisabled;

	public TurSEParameters(String query, List<String> filterQueries, TurSNFilterQueryOperator fqOperator,
						   Integer currentPage, String sort, Integer rows,
						   String group, Integer autoCorrectionDisabled) {
		super();
		this.query = query;
		this.filterQueries = filterQueries;
		this.fqOperator = fqOperator;
		this.currentPage = currentPage;
		this.sort = sort;
		this.rows = rows;
		this.group = group;
		this.autoCorrectionDisabled = autoCorrectionDisabled;
	}
}
