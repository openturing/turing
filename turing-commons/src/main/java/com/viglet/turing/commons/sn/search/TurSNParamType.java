package com.viglet.turing.commons.sn.search;

public class TurSNParamType {
	private TurSNParamType() {
		throw new IllegalStateException("Parameter Type class");
	}

	public static final String QUERY = "q";
	public static final String PAGE = "p";
	public static final String FILTER_QUERIES_DEFAULT = "fq[]";
	public static final String FILTER_QUERIES_AND = "fq.and[]";
	public static final String FILTER_QUERIES_OR = "fq.or[]";
	public static final String FILTER_QUERY_OPERATOR = "fq.op";
	public static final String FILTER_QUERY_ITEM_OPERATOR = "fqi.op";
	public static final String SORT = "sort";
	public static final String ROWS = "rows";
	public static final String GROUP = "group";
	public static final String LOCALE = "_setlocale";
	public static final String FIELD_LIST = "fl";
	public static final String AUTO_CORRECTION_DISABLED = "nfpr";
	/** T856 — response-shape flag, honoured on both the GET and the POST entry points. */
	public static final String RESPONSE_FORMAT = "responseFormat";
	/** T857 — opt-in flag that inlines the site's sort options in the structured response. */
	public static final String INCLUDE_SORT_OPTIONS = "includeSortOptions";

}
