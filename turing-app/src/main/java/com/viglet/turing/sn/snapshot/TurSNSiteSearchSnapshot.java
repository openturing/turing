/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.snapshot;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;

/**
 * Immutable, atomic snapshot of every persistent piece of configuration the SN
 * search flow needs for one (site, locale) pair. Built once on first access by
 * {@link TurSNSiteSearchSnapshotService} and cached so subsequent searches do
 * not touch the database. Eviction is centralized in
 * {@link TurSNSiteSnapshotEvictionListener}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurSNSiteSearchSnapshot(
        TurSNSite site,
        Locale locale,
        String pluginType,
        boolean hasHlFields,
        List<TurSNSiteFieldExt> enabledFields,
        Set<String> fieldIdsWithCustomFacets,
        Map<String, List<TurSNSiteCustomFacet>> customFacetsByFieldId,
        Map<String, Set<TurSNSiteFieldExtFacet>> facetLabelsByFieldId,
        List<TurSNSiteLocale> allLocales,
        List<TurSNSiteCustomSort> customSorts) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public String siteId() {
        return site != null ? site.getId() : null;
    }

    public String siteName() {
        return site != null ? site.getName() : null;
    }
}
