/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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

import java.util.List;

import com.vignette.as.client.common.AsLocaleData;

public interface IHandlerConfiguration {
	public final static String ID_ATTRIBUTE = "id";
	public final static String TYPE_ATTRIBUTE = "type";
	public final static String PROVIDER_ATTRIBUTE = "provider";
	
	String getTuringURL();
	
	String getProviderName();
	
	TurSNSiteConfig getDefaultSNSiteConfig();

	TurSNSiteConfig getSNSiteConfig(String site);

	TurSNSiteConfig getSNSiteConfig(String site, String locale);

	TurSNSiteConfig getSNSiteConfig(String site, AsLocaleData asLocaleData);

	String getMappingsXML();

	List<String> getSitesAssocPriority();

	String getCDAContextName();

	String getCDAURLPrefix();

	String getCDAURLPrefix(String site);

	String getCDAContextName(String site);

	String getLogin();

	String getPassword();

}
