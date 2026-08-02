/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.commons.sn.bean.structured;

import java.io.Serializable;
import java.util.List;

import com.viglet.turing.commons.se.field.TurSEFieldType;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T708 / §XLV — a facet group in the link-free response.
 *
 * <p>
 * {@code clearFilterQueries} is the structured form of the legacy
 * {@code cleanUpLink}: instead of navigating a URL that happens to have this
 * field's filters stripped, the client removes exactly these entries from its
 * {@code fq} list.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredFacetBean implements Serializable {

    /** Field name this facet is built on. */
    private String name;

    /** Localized group header. */
    private String label;

    /** Language tag the label was resolved for. */
    private String labelLang;

    private String description;

    private TurSEFieldType type;

    private boolean multiValued;

    /** Filter queries of this field currently applied to the request. */
    private List<String> selectedFilterQueries;

    /**
     * Remove these from the request's {@code fq} to clear this facet — the
     * link-free replacement for {@code cleanUpLink}.
     */
    private List<String> clearFilterQueries;

    private List<TurSNStructuredFacetItemBean> items;
}
