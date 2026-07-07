/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona;

/**
 * How a {@link TurPersonaSource} is ingested into the persona "notebook"
 * (Block AA / §XXVI.2). Each type maps to an already-present extraction path:
 *
 * <ul>
 *   <li>{@link #SN_DOC} — an indexed Semantic Navigation document referenced by
 *       id; its searchable text is pulled from the search engine.</li>
 *   <li>{@link #ASSET} — an uploaded PDF/DOC routed through
 *       {@code TurFileUtils.documentToText(...)}.</li>
 *   <li>{@link #URL} — a remote page fetched through
 *       {@code TurFileUtils.urlContentToText(...)} behind the existing SSRF
 *       guard.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaSourceType {
    SN_DOC,
    ASSET,
    URL
}
