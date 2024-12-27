package com.viglet.turing.connector.cms.config;

import java.net.URL;
import java.util.Collection;

public interface IHandlerConfiguration {

	URL getTuringURL();
	
	String getProviderName();
	
	TurSNSiteConfig getDefaultSNSiteConfig();

	TurSNSiteConfig getSNSiteConfig(String site);

	TurSNSiteConfig getSNSiteConfig(String site, String locale);

	String getMappingFile();

	String getCDAContextName();

	String getCDAURLPrefix();

	String getApiKey();

	String getOncePatternPath();

	String getCmsHost();

	String getCmsUsername();

	String getCmsPassword() ;

	String getCmsGroup();

	String getCmsContentType();

	String getCmsSubType();

	String getCmsRootPath();
}
