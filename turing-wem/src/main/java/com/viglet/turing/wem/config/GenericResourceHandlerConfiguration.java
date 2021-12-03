/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.wem.config;

import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.config.ConfigUtil;
import com.vignette.config.client.common.ConfigException;
import com.vignette.logging.context.ContextLogger;
import com.vignette.util.CustomerMsg;
import com.vignette.util.MsgObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/* Updated to add cda properties */
public class GenericResourceHandlerConfiguration implements IHandlerConfiguration {

	public static final String RESOURCE_TYPE = "Properties";
	public static final String RESOURCE_NAME = "VigletTuring";
	public static final String ID_ATTRIBUTE = "id";
	public static final String TYPE_ATTRIBUTE = "type";
	public static final String PROVIDER_ATTRIBUTE = "provider";
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
	private String login;
	private String password;
	private String providerName;
	private static final ContextLogger log = ContextLogger.getLogger(GenericResourceHandlerConfiguration.class);

	// Load up from Generic Resource
	public GenericResourceHandlerConfiguration() {
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
		try {
			String propertiesBody = ConfigUtil.getGenericResourceValue(RESOURCE_TYPE, RESOURCE_NAME);
			if (log.isDebugEnabled()) {
				log.debug(propertiesBody);
			}

			if (propertiesBody == null) {
				genericResourceDoesNotExistMessage();
			}

			StringReader propsBodyStream = new StringReader(propertiesBody);

			Properties properties = new Properties();

			properties.load(propsBodyStream);

			parseProperties(properties);

		} catch (ConfigException e) {
			errorLoadingGenericResourceMessage(e);
		} catch (IOException e) {
			genericResourceDoesNotExistMessage();
		}
	}

	private String getDynamicProperties(String property) {
		try {
			String propertiesBody = ConfigUtil.getGenericResourceValue(RESOURCE_TYPE, RESOURCE_NAME);
			if (log.isDebugEnabled()) {
				log.debug(propertiesBody);
			}

			if (propertiesBody == null) {
				genericResourceDoesNotExistMessage();
			}

			StringReader propsBodyStream = new StringReader(propertiesBody);

			Properties properties = new Properties();

			properties.load(propsBodyStream);

			return properties.getProperty(property);

		} catch (ConfigException e) {
			errorLoadingGenericResourceMessage(e);
		} catch (IOException e) {
			genericResourceDoesNotExistMessage();
		}
		return null;
	}

	private void errorLoadingGenericResourceMessage(ConfigException e) {
		MsgObject msg = CustomerMsg.getMsgObject("Error loading generic resource [" + RESOURCE_NAME + "]");
		log.error(msg, e);
	}

	private void genericResourceDoesNotExistMessage() {
		MsgObject msg = CustomerMsg
				.getMsgObject("Generic Resource [" + RESOURCE_NAME + "] is empty or does not exist.");
		log.error(msg);
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

		if (locale == null) {
			// For example: dps.site.default.sn.locale=en_US
			turSNSiteConfig.setLocale(snLocale);
		} else {
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
		return turSNSiteConfig;
	}

	@Override
	public TurSNSiteConfig getDefaultSNSiteConfig() {
		return new TurSNSiteConfig(snSite, snLocale);
	}

	@Override
	public TurSNSiteConfig getSNSiteConfig(String site, AsLocaleData asLocaleData) {
		TurSNSiteConfig snSiteConfig = null;
		if (asLocaleData != null && asLocaleData.getCountry() != null && asLocaleData.getLanguage() != null) {
			String locale = String.format("%s_%s", asLocaleData.getLanguage(), asLocaleData.getCountry());
			snSiteConfig = getSNSiteConfig(site, locale);
		} else if (asLocaleData != null && asLocaleData.getLanguage() != null) {
			snSiteConfig = getSNSiteConfig(site, asLocaleData.getCountry());
		}
		if (snSiteConfig == null) {
			snSiteConfig = getSNSiteConfig(site);
		}
		return snSiteConfig;
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
