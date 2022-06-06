/*
 * Copyright (C) 2016-2022 the original author or authors. 
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
package com.viglet.turing.plugins.nlp.otca.af.xml;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFConfigParameterNameEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AFConfigParameterNameEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="affixAuthorityName"/>
 *     &lt;enumeration value="affixSeparatorPattern"/>
 *     &lt;enumeration value="affixSeparatorMaxLength"/>
 *     &lt;enumeration value="searchType"/>
 *     &lt;enumeration value="extractionType"/>
 *     &lt;enumeration value="importance"/>
 *     &lt;enumeration value="otherImportance"/>
 *     &lt;enumeration value="actualImportance"/>
 *     &lt;enumeration value="dynamicCorrectionFactor"/>
 *     &lt;enumeration value="simpleAddCorrectionFactor"/>
 *     &lt;enumeration value="powerMeanExponent"/>
 *     &lt;enumeration value="lowWeightAddFactor"/>
 *     &lt;enumeration value="highWeightAddFactor"/>
 *     &lt;enumeration value="lowWeightDefinitionThreshold"/>
 *     &lt;enumeration value="highWeightDefinitionThreshold"/>
 *     &lt;enumeration value="contiguitySeparatorPattern"/>
 *     &lt;enumeration value="contiguitySeparatorMaxLength"/>
 *     &lt;enumeration value="disambiguationSeparatorPattern"/>
 *     &lt;enumeration value="disambiguationSeparatorMaxLength"/>
 *     &lt;enumeration value="entityThreshold"/>
 *     &lt;enumeration value="candidateThreshold"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AFConfigParameterNameEnum")
@XmlEnum
public enum AFConfigParameterNameEnum {

    @XmlEnumValue("affixAuthorityName")
    AFFIX_AUTHORITY_NAME("affixAuthorityName"),
    @XmlEnumValue("affixSeparatorPattern")
    AFFIX_SEPARATOR_PATTERN("affixSeparatorPattern"),
    @XmlEnumValue("affixSeparatorMaxLength")
    AFFIX_SEPARATOR_MAX_LENGTH("affixSeparatorMaxLength"),
    @XmlEnumValue("searchType")
    SEARCH_TYPE("searchType"),
    @XmlEnumValue("extractionType")
    EXTRACTION_TYPE("extractionType"),
    @XmlEnumValue("importance")
    IMPORTANCE("importance"),
    @XmlEnumValue("otherImportance")
    OTHER_IMPORTANCE("otherImportance"),
    @XmlEnumValue("actualImportance")
    ACTUAL_IMPORTANCE("actualImportance"),
    @XmlEnumValue("dynamicCorrectionFactor")
    DYNAMIC_CORRECTION_FACTOR("dynamicCorrectionFactor"),
    @XmlEnumValue("simpleAddCorrectionFactor")
    SIMPLE_ADD_CORRECTION_FACTOR("simpleAddCorrectionFactor"),
    @XmlEnumValue("powerMeanExponent")
    POWER_MEAN_EXPONENT("powerMeanExponent"),
    @XmlEnumValue("lowWeightAddFactor")
    LOW_WEIGHT_ADD_FACTOR("lowWeightAddFactor"),
    @XmlEnumValue("highWeightAddFactor")
    HIGH_WEIGHT_ADD_FACTOR("highWeightAddFactor"),
    @XmlEnumValue("lowWeightDefinitionThreshold")
    LOW_WEIGHT_DEFINITION_THRESHOLD("lowWeightDefinitionThreshold"),
    @XmlEnumValue("highWeightDefinitionThreshold")
    HIGH_WEIGHT_DEFINITION_THRESHOLD("highWeightDefinitionThreshold"),
    @XmlEnumValue("contiguitySeparatorPattern")
    CONTIGUITY_SEPARATOR_PATTERN("contiguitySeparatorPattern"),
    @XmlEnumValue("contiguitySeparatorMaxLength")
    CONTIGUITY_SEPARATOR_MAX_LENGTH("contiguitySeparatorMaxLength"),
    @XmlEnumValue("disambiguationSeparatorPattern")
    DISAMBIGUATION_SEPARATOR_PATTERN("disambiguationSeparatorPattern"),
    @XmlEnumValue("disambiguationSeparatorMaxLength")
    DISAMBIGUATION_SEPARATOR_MAX_LENGTH("disambiguationSeparatorMaxLength"),
    @XmlEnumValue("entityThreshold")
    ENTITY_THRESHOLD("entityThreshold"),
    @XmlEnumValue("candidateThreshold")
    CANDIDATE_THRESHOLD("candidateThreshold");
    private final String value;

    AFConfigParameterNameEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AFConfigParameterNameEnum fromValue(String v) {
        for (AFConfigParameterNameEnum c: AFConfigParameterNameEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
