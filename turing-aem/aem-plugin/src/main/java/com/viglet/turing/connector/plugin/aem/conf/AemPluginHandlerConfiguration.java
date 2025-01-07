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
package com.viglet.turing.connector.plugin.aem.conf;

import com.viglet.turing.connector.aem.commons.config.IAemConfiguration;
import com.viglet.turing.connector.aem.commons.config.TurAemSNSiteConfig;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.plugin.aem.persistence.model.TurAemSource;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;

@Slf4j
public class AemPluginHandlerConfiguration implements IAemConfiguration {
    private final TurAemSource turAemSource;
    private final URI turingURL;
    private final String snSite;
    private final Locale snLocale;
    private final String mappingFile;
    private final String cdaURLPrefix;
    private final String apiKey;
    private final String providerName;
    private final String oncePatternPath;

    private final String cmsHost;
    private final String cmsUsername;
    private final String cmsPassword;
    private final String cmsGroup;
    private final String cmsContentType;
    private final String cmsSubType;
    private final String cmsRootPath;

    public AemPluginHandlerConfiguration(TurAemSource turAemSource) {
        this.turAemSource = turAemSource;
        turingURL = URI.create(turAemSource.getTuringUrl());

        apiKey = turAemSource.getTuringApiKey();
        mappingFile = null;
        providerName = DEFAULT_PROVIDER;
        // DPS
        snSite = DEFAULT_SN_SITE;
        snLocale = Locale.of(DEFAULT_SN_LOCALE);
        cdaURLPrefix =   turAemSource.getUrlPrefix();
        oncePatternPath = turAemSource.getOncePattern();

        cmsHost =   turAemSource.getUrl();
        cmsUsername = turAemSource.getUsername();
        cmsPassword = turAemSource.getPassword();
        cmsGroup = turAemSource.getGroup();
        cmsContentType = turAemSource.getContentType();
        cmsSubType = null;
        cmsRootPath = turAemSource.getRootPath();
    }

    @Override
    public String getCmsHost() {
        return cmsHost;
    }

    @Override
    public String getCmsUsername() {
        return cmsUsername;
    }

    @Override
    public String getCmsPassword() {
        return cmsPassword;
    }

    @Override
    public String getCmsGroup() {
        return cmsGroup;
    }

    @Override
    public String getCmsContentType() {
        return cmsContentType;
    }

    @Override
    public String getCmsSubType() {
        return cmsSubType;
    }

    @Override
    public String getCmsRootPath() {
        return cmsRootPath;
    }

    @Override
    public URI getTuringURL() {
        return turingURL;
    }

    @Override
    public String getMappingFile() {
        return mappingFile;
    }

    @Override
    public String getCDAURLPrefix() {
        return cdaURLPrefix;
    }
    @Override
    public String getOncePatternPath() {
        return oncePatternPath;
    }

    public Collection<TurAemLocalePathContext> getLocales() {
        Collection<TurAemLocalePathContext> turAemLocalePathContexts = new HashSet<>();
     turAemSource.getLocalePaths().forEach(localePath ->
             turAemLocalePathContexts.add(TurAemLocalePathContext.builder()
             .snSite(localePath.getTurAemSource().getTurSNSites().stream().findFirst().get())
             .path(localePath.getPath())
             .locale(localePath.getLocale())
             .build()));

        return turAemLocalePathContexts;
    }

    @Override
    public TurAemSNSiteConfig getDefaultSNSiteConfig() {
        return new TurAemSNSiteConfig(snSite, snLocale);
    }

    @Override
    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }
}
