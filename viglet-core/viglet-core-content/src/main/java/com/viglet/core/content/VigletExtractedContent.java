/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.content;

import java.io.File;

import org.apache.tika.metadata.Metadata;

/**
 * Neutral result of an Apache Tika extraction: the source {@link File} (may be
 * {@code null} for stream-only input), the extracted plain text, and the raw
 * Tika {@link Metadata}. Products adapt this into their own DTOs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record VigletExtractedContent(File file, String content, Metadata metadata) {
}
