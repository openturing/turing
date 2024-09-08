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

import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

@Slf4j
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
    private String cmsSubType;
    private String cmsRootPath;

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

    private final String propertyFile;
    // Load up from Generic Resource

    public AemHandlerConfiguration(String propertyFile) {
        this.propertyFile = propertyFile;
        parsePropertiesFromResource();
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
        parseProperties(getProperties());
    }

    private Properties getProperties() {
        Properties properties = new Properties();
        try {
            if (new File(propertyFile).exists()) {
                properties.load(new FileReader(propertyFile));
            } else {
                log.info("ERROR: Cannot open {} file, use --property parameter correctly", propertyFile);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return properties;
    }

    private String getDynamicProperties(String property) {
        return getProperties().getProperty(property);

    }

    private void parseProperties(Properties properties) {

        // Turing
        try {
            turingURL = URI.create(properties.getProperty("turing.url", DEFAULT_TURING_URL)).toURL();
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        apiKey = properties.getProperty("turing.apiKey");
        mappingFile = properties.getProperty("turing.mapping.file", DEFAULT_CTD_MAPPING_FILE);
        providerName = properties.getProperty("turing.provider.name", DEFAULT_PROVIDER);
        // DPS
        snSite = properties.getProperty("dps.site.default.sn.site", DEFAULT_SN_SITE);
        snLocale = LocaleUtils.toLocale(properties.getProperty("dps.site.default.sn.locale", DEFAULT_SN_LOCALE));
        cdaContextName = properties.getProperty("dps.site.default.context.name", DEFAULT_DPS_CONTEXT);
        cdaURLPrefix = properties.getProperty("dps.site.default.url.prefix");
        oncePatternPath = properties.getProperty("sn.default.once.pattern.path");

        cmsHost = properties.getProperty("cms.url");
        cmsUsername = properties.getProperty("cms.username");
        cmsPassword = properties.getProperty("cms.password");
        cmsGroup = properties.getProperty("cms.group");
        cmsContentType = properties.getProperty("cms.content-type");
        cmsSubType = properties.getProperty("cms.sub-type");
        cmsRootPath = properties.getProperty("cms.root.path");
    }

    @Override
    public TurSNSiteConfig getSNSiteConfig(String site, String locale) {
        // For example: dps.site.Intranet.en.sn.site=Intra
        return setSiteName(site, locale)
                .setLocale(LocaleUtils.toLocale(Objects.requireNonNullElse(
                        getDynamicProperties(String.format("dps.site.%s.%s.sn.locale", site, locale)), locale)));
    }

    private TurSNSiteConfig setSiteName(String site, String locale) {
        String snSiteInternal = getDynamicProperties(String.format("dps.site.%s.%s.sn.site", site, locale));
        return StringUtils.isEmpty(snSiteInternal) ? getSNSiteConfig(site) :
                getDefaultSNSiteConfig().setName(snSiteInternal);
    }

    @Override
    public TurSNSiteConfig getSNSiteConfig(String site) {
        TurSNSiteConfig turSNSiteConfig = getDefaultSNSiteConfig();
        return turSNSiteConfig.setName(Objects.requireNonNullElse(
                        getDynamicProperties(String.format("dps.site.%s.sn.site", site)),
                        turSNSiteConfig.getName()))
                .setLocale(Objects.requireNonNullElse(
                        LocaleUtils.toLocale(getDynamicProperties(String.format("dps.site.%s.sn.locale", site))),
                        snLocale));
    }

    public Collection<TurAemLocalePathContext> getLocales() {
        Collection<TurAemLocalePathContext> turAemLocalePathContexts = new HashSet<>();
        for (Enumeration<?> e = getProperties().propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String[] tokens = name.split("\\.");
            if (tokens.length == 4 && name.startsWith("sn.") && name.endsWith(".path")) {
                turAemLocalePathContexts.add(TurAemLocalePathContext.builder()
                        .snSite(tokens[1])
                        .path(getProperties().getProperty(name))
                        .locale(LocaleUtils.toLocale(tokens[2]))
                        .build());
            }
        }
        return turAemLocalePathContexts;
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
