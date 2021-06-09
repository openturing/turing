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

    String getTuringURL();
    String getSNSite();
    String getSNSite(String locale);
    String getSNSite(AsLocaleData asLocaleData);
    String getMappingsXML();
    List<String> getSitesAssocPriority();
    String getCDAContextName(); 
    String getCDAFormatName();
    String getCDAURLPrefix();
    String getCDAURLPrefix(String site);
    String getCDAContextName(String site);
    String getCDAFormatName(String site); 
    boolean hasSiteName(String site);
    boolean hasContext(String site);
    boolean hasFormat(String site);
	boolean isLive();
	String getLogin();
	String getPassword();
   
}
