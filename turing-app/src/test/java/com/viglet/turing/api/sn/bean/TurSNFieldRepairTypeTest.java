/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.api.sn.bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSNFieldRepairType enum.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSNFieldRepairTypeTest {

    @Test
    void testEnumValues() {
        TurSNFieldRepairType[] values = TurSNFieldRepairType.values();
        assertThat(values).hasSize(4).contains(
                TurSNFieldRepairType.SE_CREATE_FIELD,
                TurSNFieldRepairType.SE_CHANGE_TYPE,
                TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE,
                TurSNFieldRepairType.SN_CHANGE_TYPE);
    }

    @Test
    void testValueOf() {
        assertThat(TurSNFieldRepairType.valueOf("SE_CREATE_FIELD"))
                .isEqualTo(TurSNFieldRepairType.SE_CREATE_FIELD);
        assertThat(TurSNFieldRepairType.valueOf("SE_CHANGE_TYPE"))
                .isEqualTo(TurSNFieldRepairType.SE_CHANGE_TYPE);
        assertThat(TurSNFieldRepairType.valueOf("SE_ENABLE_MULTI_VALUE"))
                .isEqualTo(TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE);
        assertThat(TurSNFieldRepairType.valueOf("SN_CHANGE_TYPE"))
                .isEqualTo(TurSNFieldRepairType.SN_CHANGE_TYPE);
    }

    @Test
    void testValueOfInvalid() {
        assertThatThrownBy(() -> TurSNFieldRepairType.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSECreateField() {
        TurSNFieldRepairType type = TurSNFieldRepairType.SE_CREATE_FIELD;

        assertThat(type).isNotNull();
        assertThat(type.name()).hasToString("SE_CREATE_FIELD");
        assertThat(type.toString()).hasToString("SE_CREATE_FIELD");
    }

    @Test
    void testSEChangeType() {
        TurSNFieldRepairType type = TurSNFieldRepairType.SE_CHANGE_TYPE;

        assertThat(type).isNotNull();
        assertThat(type.name()).hasToString("SE_CHANGE_TYPE");
        assertThat(type.toString()).hasToString("SE_CHANGE_TYPE");
    }

    @Test
    void testSEEnableMultiValue() {
        TurSNFieldRepairType type = TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE;

        assertThat(type).isNotNull();
        assertThat(type.name()).hasToString("SE_ENABLE_MULTI_VALUE");
        assertThat(type.toString()).hasToString("SE_ENABLE_MULTI_VALUE");
    }

    @Test
    void testSNChangeType() {
        TurSNFieldRepairType type = TurSNFieldRepairType.SN_CHANGE_TYPE;

        assertThat(type).isNotNull();
        assertThat(type.name()).hasToString("SN_CHANGE_TYPE");
        assertThat(type.toString()).hasToString("SN_CHANGE_TYPE");
    }

    @Test
    void testEnumEquality() {
        TurSNFieldRepairType type1 = TurSNFieldRepairType.SE_CREATE_FIELD;
        TurSNFieldRepairType type2 = TurSNFieldRepairType.SE_CREATE_FIELD;
        TurSNFieldRepairType type3 = TurSNFieldRepairType.SE_CHANGE_TYPE;

        assertThat(type1).isEqualTo(type2).isNotEqualTo(type3);
    }

    @Test
    void testEnumInSwitch() {
        TurSNFieldRepairType type = TurSNFieldRepairType.SE_CREATE_FIELD;

        String result = switch (type) {
            case SE_CREATE_FIELD -> "Create Field";
            case SE_CHANGE_TYPE -> "Change Type";
            case SE_ENABLE_MULTI_VALUE -> "Enable Multi Value";
            case SN_CHANGE_TYPE -> "SN Change Type";
        };

        assertThat(result).isEqualTo("Create Field");
    }

    @Test
    void testAllEnumConstantsAreDifferent() {
        TurSNFieldRepairType[] values = TurSNFieldRepairType.values();

        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertThat(values[i]).isNotEqualTo(values[j]);
            }
        }
    }

    @Test
    void testEnumOrdinals() {
        assertThat(TurSNFieldRepairType.SE_CREATE_FIELD.ordinal()).isZero();
        assertThat(TurSNFieldRepairType.SE_CHANGE_TYPE.ordinal()).isEqualTo(1);
        assertThat(TurSNFieldRepairType.SE_ENABLE_MULTI_VALUE.ordinal()).isEqualTo(2);
        assertThat(TurSNFieldRepairType.SN_CHANGE_TYPE.ordinal()).isEqualTo(3);
    }

    @Test
    void testEnumComparison() {
        assertThat(TurSNFieldRepairType.SE_CREATE_FIELD.compareTo(TurSNFieldRepairType.SE_CHANGE_TYPE))
                .isLessThan(0);
        assertThat(TurSNFieldRepairType.SN_CHANGE_TYPE.compareTo(TurSNFieldRepairType.SE_CREATE_FIELD))
                .isGreaterThan(0);
        assertThat(TurSNFieldRepairType.SE_CHANGE_TYPE.compareTo(TurSNFieldRepairType.SE_CHANGE_TYPE))
            .isEqualByComparingTo(0);
    }
}
