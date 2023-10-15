/*
 * Copyright (C) 2016-2023 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.connector.aem.indexer.conf;

import com.viglet.turing.connector.aem.indexer.TurAEMIndexerTool;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class AemHandlerConfiguration implements IHandlerConfiguration {
	private static Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public static final String RESOURCE_TYPE = "Properties";
	public static final String RESOURCE_NAME = "VigletTuring";
	public static final String ID_ATTRIBUTE = "id";
	public static final String TYPE_ATTRIBUTE = "type";
	public static final String PROVIDER_ATTRIBUTE = "source_apps";
	private static final String DEFAULT_PROVIDER = "WEM";
	private static final String DEFAULT_TURING_URL = "http://localhost:2700";
	private static final String DEFAULT_TURING_USERNAME = "admin";
	private static final String DEFAULT_TURING_PASSWORD = "admin";
	private static final String DEFAULT_CTD_MAPPING_FILE = "/CTD-Turing-Mappings.xml";
	private static final String DEFAULT_SN_SITE = "Sample";
	private static final String DEFAULT_SN_LOCALE = "en_US";
	private static final String DEFAULT_DPS_CONTEXT = "sites";

	private String turingURL;
	private String snSite;
	private String snLocale;
	private String mappingsXML;
	private String cdaContextName;
	private String cdaURLPrefix;
	private String sitesAssociationPriority;
	private String fileSourcePath;
	private String login;
	private String password;
	private String providerName;

	// Load up from Generic Resource
	public AemHandlerConfiguration() {
		parsePropertiesFromResource();
	}

	@Override
	public String getTuringURL() {
		return turingURL;
	}

	public void setTuringURL(String turingURL) {
		this.turingURL = turingURL;
	}

	@Override
	public String getMappingsXML() {
		return mappingsXML;
	}

	@Override
	public String getCDAContextName() {
		return cdaContextName;
	}

	@Override
	public String getCDAContextName(String site) {
		String contextName = getDynamicProperties("dps.site." + site + ".contextname");
		return contextName != null ? contextName : getCDAContextName();
	}

	public void setCDAContextName(String cdaContextName) {
		this.cdaContextName = cdaContextName;
	}

	@Override
	public String getCDAURLPrefix() {
		return cdaURLPrefix;
	}

	@Override
	public String getCDAURLPrefix(String site) {
		String urlPrefix = getDynamicProperties("dps.site." + site + ".urlprefix");
		return urlPrefix != null ? urlPrefix : getCDAURLPrefix();
	}

	public void setCDAURLPrefix(String cdaURLPrefix) {
		this.cdaURLPrefix = cdaURLPrefix;
	}

	private void parsePropertiesFromResource() {
		parseProperties(getProperties());
	}

	private Properties getProperties() {
		Properties properties = new Properties();
		try {
			properties.load(new FileReader(TurAEMIndexerTool.class.getProtectionDomain().getCodeSource().getLocation()
					.getFile().replaceAll(".jar$", ".properties")));
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return properties;
	}

	private String getDynamicProperties(String property) {
		return getProperties().getProperty(property);
	}

	private void parseProperties(Properties properties) {

		// Turing
		turingURL = properties.getProperty("turing.url", DEFAULT_TURING_URL);
		login = properties.getProperty("turing.login", DEFAULT_TURING_USERNAME);
		password = properties.getProperty("turing.password", DEFAULT_TURING_PASSWORD);
		mappingsXML = properties.getProperty("turing.mappingsxml", DEFAULT_CTD_MAPPING_FILE);
		providerName = properties.getProperty("turing.provider.name", DEFAULT_PROVIDER);
		// DPS
		snSite = properties.getProperty("dps.site.default.sn.site", DEFAULT_SN_SITE);
		snLocale = properties.getProperty("dps.site.default.sn.locale", DEFAULT_SN_LOCALE);
		cdaContextName = properties.getProperty("dps.site.default.contextname", DEFAULT_DPS_CONTEXT);
		cdaURLPrefix = properties.getProperty("dps.site.default.urlprefix");
		sitesAssociationPriority = properties.getProperty("dps.config.association.priority");
		fileSourcePath = properties.getProperty("dps.config.filesource.path");

	}

	@Override
	public TurSNSiteConfig getSNSiteConfig(String site, String locale) {
		TurSNSiteConfig turSNSiteConfig = getDefaultSNSiteConfig();
		// For example: dps.site.Intranet.en.sn.site=Intra
		String snSiteInternal = getDynamicProperties(String.format("dps.site.%s.%s.sn.site", site, locale));
		if (snSiteInternal == null) {
			turSNSiteConfig = getSNSiteConfig(site);
		} else {
			turSNSiteConfig.setName(snSiteInternal);
		}

		if (locale != null) {
			// For example: dps.site.Intranet.en.sn.locale=en_US
			String snLocaleInternal = getDynamicProperties(String.format("dps.site.%s.%s.sn.locale", site, locale));
			if (snLocaleInternal == null) {
				turSNSiteConfig.setLocale(locale);
			} else {
				turSNSiteConfig.setLocale(snLocaleInternal);
			}
		}
		return turSNSiteConfig;
	}

	@Override
	public TurSNSiteConfig getSNSiteConfig(String site) {
		TurSNSiteConfig turSNSiteConfig = getDefaultSNSiteConfig();

		// For example: dps.site.Intranet.sn.site=Intra
		String snSiteInternal = getDynamicProperties(String.format("dps.site.%s.sn.site", site));
		if (snSiteInternal != null) {
			turSNSiteConfig.setName(snSiteInternal);
		}

		// For example: dps.site.Intranet.sn.locale=en_US
		String snLocaleInternal = getDynamicProperties(String.format("dps.site.%s.sn.locale", site));
		if (snLocaleInternal == null) {
			// For example: dps.site.default.sn.locale=en_US
			turSNSiteConfig.setLocale(snLocale);
		} else {
			turSNSiteConfig.setLocale(snLocaleInternal);
		}
		return turSNSiteConfig;
	}

	@Override
	public TurSNSiteConfig getDefaultSNSiteConfig() {
		return new TurSNSiteConfig(snSite, snLocale);
	}

	@Override
	public String getFileSourcePath() {
		return fileSourcePath;
	}

	@Override
	public List<String> getSitesAssocPriority() {
		if (sitesAssociationPriority != null) {
			String[] sites = sitesAssociationPriority.split(",");

			List<String> siteList = new ArrayList<>();
			for (String site : sites) {
				siteList.add(site.trim());
			}
			if (!siteList.isEmpty())
				return siteList;
			else
				return Collections.emptyList();

		} else
			return Collections.emptyList();

	}

	@Override
	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		this.login = login;
	}

	@Override
	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public String getProviderName() {
		return providerName;
	}

	public void setProviderName(String providerName) {
		this.providerName = providerName;
	}

}
