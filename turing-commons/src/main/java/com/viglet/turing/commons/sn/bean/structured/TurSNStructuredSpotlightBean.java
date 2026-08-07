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

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T938 / §XLV.8 — one editorially pinned document, projected.
 *
 * <p>
 * The structured response used to carry {@code TurSNSiteSpotlightDocumentBean}
 * straight through, and that bean names the document's destination
 * {@code link}. So the block's load-bearing assertion — nothing here is a URL
 * the client has to parse — held only while the response had no spotlight in
 * it, and a field literally spelled {@code link} is the one thing a reader
 * cannot tell apart from the navigation hrefs the projection exists to remove.
 * </p>
 *
 * <p>
 * The value itself was never a navigation action: it is the document's own
 * destination, the same kind of value {@code results[].fields.url} and
 * {@code similar[].url} already carry. So it keeps crossing, under the name
 * those two use — and the guard needs no exception.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredSpotlightBean implements Serializable {

    private String id;

    /** 1-based position the document is injected into the result list at. */
    private int position;

    private String title;

    private String type;

    private String referenceId;

    private String content;

    /** The document's own destination — never a search URL. */
    private String url;
}
