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
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.sn.migration;

import com.viglet.core.manifest.VigletFieldType;

/**
 * One declarative field-mapping override applied during a migration (T660 /
 * §XXXVIII.4).
 *
 * <p>A source schema is rarely 1:1 with the desired Turing schema, so an operator
 * can reshape it on the way in without editing every record. Each override targets
 * one source field (by its dotted name) and does one or more of:</p>
 * <ul>
 *   <li><b>rename</b> — index the field under {@code rename} instead of {@code field}
 *       (applied to both the manifest field and every document attribute).</li>
 *   <li><b>retype</b> — force the field's manifest {@code type} instead of the
 *       derived/mapped one.</li>
 *   <li><b>drop</b> — exclude the field from the manifest and from every document.</li>
 *   <li><b>default</b> — inject {@code defaultValue} on any document that leaves the
 *       (renamed) field absent. Combined with {@code type} and no matching source
 *       field, this materialises a constant field the source never had.</li>
 * </ul>
 *
 * @param field        the source field name to match (dotted; required).
 * @param rename       the new field name, or {@code null} to keep {@code field}.
 * @param type         the forced field type, or {@code null} to keep the derived one.
 * @param drop         when true, the field is excluded entirely.
 * @param defaultValue value injected when the field is absent, or {@code null}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurMigrationOverride(
        String field,
        String rename,
        VigletFieldType type,
        boolean drop,
        Object defaultValue) {

    /** The name this field is indexed under (the rename when set, else the source name). */
    public String targetName() {
        return rename != null && !rename.isBlank() ? rename : field;
    }
}
