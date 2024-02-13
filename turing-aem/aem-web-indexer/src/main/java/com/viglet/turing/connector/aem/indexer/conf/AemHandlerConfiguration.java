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

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemConfigVar;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemConfigVarRepository;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Component
public class AemHandlerConfiguration implements IHandlerConfiguration {
    public static final String ID_ATTRIBUTE = "id";
    public static final String PROVIDER_ATTRIBUTE = "source_apps";
    public static final String DEFAULT_PROVIDER = "AEM";
    private static final String DEFAULT_TURING_URL = "http://localhost:2700";
    private static final String DEFAULT_CTD_MAPPING_FILE = "/CTD-Turing-Mappings.xml";
    private static final String DEFAULT_SN_SITE = "Sample";
    private static final String DEFAULT_SN_LOCALE = Locale.US.toString();
    private static final String DEFAULT_DPS_CONTEXT = "sites";
    private URL turingURL;
    private String snSite;
    private Locale snLocale;
    private String mappingFile;
    private String cdaContextName;
    private String cdaURLPrefix;
    private String apiKey;
    private String providerName;
    private String oncePatternPath;
    private String cmsHost;
    private String cmsUsername;
    private String cmsPassword;
    private String cmsGroup;
    private String cmsContentType;
    private String cmsRootPath;

    private final TurAemConfigVarRepository turAemConfigVarRepository;

    @Inject
    public AemHandlerConfiguration(TurAemConfigVarRepository turAemConfigVarRepository) {
        this.turAemConfigVarRepository = turAemConfigVarRepository;
        parsePropertiesFromResource();
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
    // Load up from Generic Resource

    @Override
    public String getCmsRootPath() {
        return cmsRootPath;
    }

    @Override
    public URL getTuringURL() {
        return turingURL;
    }

    @Override
    public String getMappingFile() {
        return mappingFile;
    }

    @Override
    public String getCDAContextName() {
        return cdaContextName;
    }

    @Override
    public String getCDAURLPrefix() {
        return cdaURLPrefix;
    }

    @Override
    public String getOncePatternPath() {
        return oncePatternPath;
    }

    private void parsePropertiesFromResource() {
        parseProperties();
    }

   private void parseProperties() {

        // Turing
        try {
            turingURL = URI.create(getProperty("turing.url", DEFAULT_TURING_URL)).toURL();
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        apiKey = getProperty("turing.apiKey");
        mappingFile = getProperty("turing.mapping.file", DEFAULT_CTD_MAPPING_FILE);
        providerName = getProperty("turing.provider.name", DEFAULT_PROVIDER);
        // DPS
        snSite = getProperty("dps.site.default.sn.site", DEFAULT_SN_SITE);
        snLocale = LocaleUtils.toLocale(getProperty("dps.site.default.sn.locale", DEFAULT_SN_LOCALE));
        cdaContextName = getProperty("dps.site.default.context.name", DEFAULT_DPS_CONTEXT);
        cdaURLPrefix = getProperty("dps.site.default.url.prefix");
        oncePatternPath = getProperty("sn.default.once.pattern.path");

        cmsHost = getProperty("cms.url");
        cmsUsername = getProperty("cms.username");
        cmsPassword = getProperty("cms.password");
        cmsGroup = getProperty("cms.group");
        cmsContentType = getProperty("cms.content-type");
        cmsRootPath = getProperty("cms.root.path");
    }

    @NotNull
    private String getProperty(String key, String defaultValue) {
        return turAemConfigVarRepository.findById(key)
                .map(TurAemConfigVar::getValue).orElse(defaultValue);
    }

    private String getProperty(String key) {
        return getProperty(key, null);
    }

    @Override
    public TurSNSiteConfig getSNSiteConfig(String site, String locale) {
        // For example: dps.site.Intranet.en.sn.site=Intra
        return setSiteName(site, locale)
                .setLocale(LocaleUtils.toLocale(Objects.requireNonNullElse(
                        getProperty(String.format("dps.site.%s.%s.sn.locale", site, locale)), locale)));
    }

    private TurSNSiteConfig setSiteName(String site, String locale) {
        String snSiteInternal = getProperty(String.format("dps.site.%s.%s.sn.site", site, locale));
        return StringUtils.isEmpty(snSiteInternal) ? getSNSiteConfig(site) :
                getDefaultSNSiteConfig().setName(snSiteInternal);
    }

    @Override
    public TurSNSiteConfig getSNSiteConfig(String site) {
        TurSNSiteConfig turSNSiteConfig = getDefaultSNSiteConfig();
        return turSNSiteConfig.setName(Objects.requireNonNullElse(
                        getProperty(String.format("dps.site.%s.sn.site", site)),
                        turSNSiteConfig.getName()))
                .setLocale(Objects.requireNonNullElse(
                        LocaleUtils.toLocale(getProperty(String.format("dps.site.%s.sn.locale", site))),
                        snLocale));
    }

    public Locale getLocaleByPath(String snSite, String path) {
        // dps.site.Intra.sn.locale.en_US.path=/content/sample/en

        for (TurAemConfigVar property : turAemConfigVarRepository.findAll()) {
            if (hasPath(snSite, path, property.getId())) {
                return LocaleUtils.toLocale(property.getId().split("\\.")[2]);
            }
        }
        return snLocale;
    }

    private boolean hasPath(String snSite, String path, String name) {
        return name.startsWith(String.format("sn.%s", snSite))
                && name.endsWith(".path") && path.startsWith(getProperty(name));
    }

    @Override
    public TurSNSiteConfig getDefaultSNSiteConfig() {
        return new TurSNSiteConfig(snSite, snLocale);
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
