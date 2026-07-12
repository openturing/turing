/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match.dto;

/**
 * Request body to add a URL or indexed-document content to a Persona Match
 * project (Block AT / §XLIII). ASSET contents are added through the multipart
 * upload endpoint instead. {@code siteName} is only used for {@code SN_DOC}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurPersonaMatchSourceRequest(
        String type,
        String sourceName,
        String ref,
        String siteName) {
}
