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

import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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
    private String mappingsXML;
    private String cdaContextName;
    private String cdaURLPrefix;
    private String sitesAssociationPriority;
    private Path fileSourcePath;
    private String apiKey;
    private String providerName;

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
    public String getMappingsXML() {
        return mappingsXML;
    }

    @Override
    public String getCDAContextName() {
        return cdaContextName;
    }

    @Override
    public String getCDAContextName(String site) {
        return Objects.requireNonNullElse(getDynamicProperties("dps.site." + site + ".contextname"),
                getCDAContextName());
    }

    @Override
    public String getCDAURLPrefix() {
        return cdaURLPrefix;
    }

    @Override
    public String getCDAURLPrefix(String site) {
        return Objects.requireNonNullElse(getDynamicProperties("dps.site." + site + ".urlprefix"),
                getCDAURLPrefix());
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
                System.out.printf("%nERROR: Cannot open %s file, use --property parameter correctly%n", propertyFile);
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
            turingURL = new URL(properties.getProperty("turing.url", DEFAULT_TURING_URL));
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        apiKey = properties.getProperty("turing.apiKey");
        mappingsXML = properties.getProperty("turing.mappingsxml", DEFAULT_CTD_MAPPING_FILE);
        providerName = properties.getProperty("turing.provider.name", DEFAULT_PROVIDER);
        // DPS
        snSite = properties.getProperty("dps.site.default.sn.site", DEFAULT_SN_SITE);
        snLocale = LocaleUtils.toLocale(properties.getProperty("dps.site.default.sn.locale", DEFAULT_SN_LOCALE));
        cdaContextName = properties.getProperty("dps.site.default.contextname", DEFAULT_DPS_CONTEXT);
        cdaURLPrefix = properties.getProperty("dps.site.default.urlprefix");
        sitesAssociationPriority = properties.getProperty("dps.config.association.priority");
        if (properties.contains("dps.config.filesource.path")) {
            fileSourcePath = Paths.get(properties.getProperty("dps.config.filesource.path"));
        }
    }

    @Override
    public TurSNSiteConfig getSNSiteConfig(String site, @NotNull String locale) {
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

    public Locale getLocaleByPath(String snSite, String path) {
        // dps.site.Intra.sn.locale.en_US.path=/content/sample/en
        for (Enumeration<?> e = getProperties().propertyNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            if (hasPath(snSite, path, name)) {
                return LocaleUtils.toLocale(name.split("\\.")[2]);
            }
        }
        return snLocale;
    }

    private boolean hasPath(String snSite, String path, String name) {
        return name.startsWith(String.format("sn.%s", snSite))
                && name.endsWith(".path") && path.startsWith(getProperties().getProperty(name));
    }

    @Override
    public TurSNSiteConfig getDefaultSNSiteConfig() {
        return new TurSNSiteConfig(snSite, snLocale);
    }

    @Override
    public Path getFileSourcePath() {
        return fileSourcePath;
    }

    @Override
    public List<String> getSitesAssocPriority() {
        return !StringUtils.isEmpty(sitesAssociationPriority) ? Arrays.stream(
                sitesAssociationPriority.split(",")).map(String::trim).collect(Collectors.toList())
                : Collections.emptyList();
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
