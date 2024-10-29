package com.viglet.turing.commons.se;

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
