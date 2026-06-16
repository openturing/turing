/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.core.commons.file;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * The content and metadata extracted from a file: plain-text content, name,
 * title, extension, {@link VigletFileSize}, last-modified date and a free-form
 * metadata map.
 *
 * <p>Neutral home (Block Q / T375) for the DTO the three products hand-synced
 * identically. Deliberately neutral: no {@code Tur*} naming and no dependency
 * on any product module. A product may keep its own named DTO (bound to its
 * published SDK) until the Phase-2 unification.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@ToString
public class VigletFileAttributes {
    private String content;
    private String name;
    private String title;
    private String extension;
    @Builder.Default
    private VigletFileSize size = new VigletFileSize();
    @Builder.Default
    private Date lastModified = new Date();
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}
