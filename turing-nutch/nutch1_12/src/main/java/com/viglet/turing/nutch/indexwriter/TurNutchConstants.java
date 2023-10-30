package com.viglet.turing.nutch.indexwriter;

public interface TurNutchConstants {
	public static final String TUR_PREFIX = "turing.";
	
	public static String WEIGHT_FIELD = TUR_PREFIX + "weight.field";
	
	public final String SERVER_URL = TUR_PREFIX + "url";

	public final String SITE = TUR_PREFIX + "site";

	public final String USE_AUTH = TUR_PREFIX + "auth";

	public final String USERNAME = TUR_PREFIX + "username";

	public final String PASSWORD = TUR_PREFIX + "password";
	
	public final String FORCE_CONFIG = TUR_PREFIX + "force.config";
	
	public final String LOCALE_PROPERTY = TUR_PREFIX + "locale";
	
	public final String LOCALE_FIELD_PROPERTY = TUR_PREFIX + "locale.field";

	public final String TIMESTAMP_PROPERTY = TUR_PREFIX + "timestamp.field";
	
	public final String SOLR_SERVER_URL = "solr.server.url";
}
