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
package com.viglet.core.logging;

import java.io.Serializable;
import java.util.Date;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Neutral general-log entry serialized by the MongoDB / Redis appenders. The
 * field set (and therefore the persisted document shape) is identical across
 * Viglet products.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Builder
@Getter
@Setter
public class VigletLoggingEntry implements Serializable {
    private String clusterNode;
    private String level;
    private String logger;
    private String message;
    private String stackTrace;
    @JsonSerialize(using = VigletIsoDateSerializer.class)
    private Date date;
}
