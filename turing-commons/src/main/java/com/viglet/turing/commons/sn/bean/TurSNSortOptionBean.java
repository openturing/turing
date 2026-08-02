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
package com.viglet.turing.commons.sn.bean;

import java.io.Serializable;

/**
 * One selectable sort for a site: the value to put on {@code request.sort} and
 * the label to render.
 *
 * <p>
 * T857 / §LXV.2 moved this out of {@code TurSNSiteSearchAPI} so the same record
 * can be served by {@code GET /api/sn/{site}/search/sort-options} <em>and</em>
 * inlined into the structured search response — one shape, one source, no way
 * for the two surfaces to disagree. The JSON is unchanged
 * ({@code {"value": …, "label": …}}).
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNSortOptionBean(String value, String label) implements Serializable {
}
