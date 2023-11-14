package com.viglet.turing.connector.cms.config;

import java.util.List;

public interface IHandlerConfiguration {

	String getTuringURL();
	
	String getProviderName();
	
	TurSNSiteConfig getDefaultSNSiteConfig();

	TurSNSiteConfig getSNSiteConfig(String site);

	TurSNSiteConfig getSNSiteConfig(String site, String locale);

	String getMappingsXML();

	List<String> getSitesAssocPriority();

	String getCDAContextName();

	String getCDAURLPrefix();

	String getCDAURLPrefix(String site);

	String getCDAContextName(String site);

	String getApiKey();

	String getFileSourcePath();
}
