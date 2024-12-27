/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.commons.config;

import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;

import java.net.URL;
import java.util.Collection;
import java.util.Locale;

public interface IAemConfiguration {
    String DEFAULT_PROVIDER = "AEM";
    String DEFAULT_TURING_URL = "http://localhost:2700";
    String DEFAULT_CTD_MAPPING_FILE = "/CTD-Turing-Mappings.xml";
    String DEFAULT_SN_SITE = "Sample";
    String DEFAULT_SN_LOCALE = Locale.US.toString();
    String DEFAULT_DPS_CONTEXT = "sites";

    URL getTuringURL();

    String getProviderName();

    TurAemSNSiteConfig getDefaultSNSiteConfig();

    TurAemSNSiteConfig getSNSiteConfig(String site);

    TurAemSNSiteConfig getSNSiteConfig(String site, String locale);

    String getMappingFile();

    String getCDAContextName();

    String getCDAURLPrefix();

    String getApiKey();

    String getOncePatternPath();

    String getCmsHost();

    String getCmsUsername();

    String getCmsPassword();

    String getCmsGroup();

    String getCmsContentType();

    String getCmsSubType();

    String getCmsRootPath();

    Collection<TurAemLocalePathContext> getLocales();
}
