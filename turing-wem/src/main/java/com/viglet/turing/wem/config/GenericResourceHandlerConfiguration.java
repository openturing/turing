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

	private String turingURL;
	private String snSite;
	private String mappingsXML;
	private String cdaContextName;
	private String cdaURLPrefix;
	private String sitesAssociationPriority;
	private String login;
	private String password;

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
	public String getDefaultSNSite() {
		return snSite;
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
		turingURL = properties.getProperty("turing.url");
		login = properties.getProperty("turing.login");
		password = properties.getProperty("turing.password");
		mappingsXML = properties.getProperty("turing.mappingsxml", "/CTD-Turing-Mappings.xml");

		// DPS
		snSite = properties.getProperty("dps.site.default.snsite");
		cdaContextName = properties.getProperty("dps.site.default.contextname");
		cdaURLPrefix = properties.getProperty("dps.site.default.urlprefix");
		sitesAssociationPriority = properties.getProperty("dps.config.association.priority");

	}

	@Override
	public String getSNSite(String site) {
		String snSite = getDynamicProperties(String.format("dps.site.%s.snsite", site));
		return snSite != null ? snSite : getDefaultSNSite();
	}

	@Override
	public String getSNSite(String site, String locale) {
		String snSite = getDynamicProperties(String.format("dps.site.%s.%s.snsite", site, locale));
		return snSite != null ? snSite : getSNSite(site);
	}

	@Override
	public String getSNSite(String site, AsLocaleData asLocaleData) {
		String snSiteInternal = null;
		if (asLocaleData != null && asLocaleData.getCountry() != null && asLocaleData.getLanguage() != null) {
			String locale = String.format("%s_%s", asLocaleData.getLanguage(), asLocaleData.getCountry());
			snSiteInternal = getSNSite(site, locale);
		} else if (asLocaleData != null && asLocaleData.getLanguage() != null) {
			snSiteInternal = getSNSite(site, asLocaleData.getCountry());
		}
		if (snSiteInternal == null) {
			snSiteInternal = getSNSite(site);
		}

		return snSiteInternal;
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

}
