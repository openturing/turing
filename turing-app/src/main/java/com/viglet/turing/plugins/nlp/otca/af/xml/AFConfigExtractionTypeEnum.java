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

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFConfigExtractionTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AFConfigExtractionTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="dynamic"/>
 *     &lt;enumeration value="entity"/>
 *     &lt;enumeration value="candidate"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AFConfigExtractionTypeEnum")
@XmlEnum
public enum AFConfigExtractionTypeEnum {

    @XmlEnumValue("dynamic")
    DYNAMIC("dynamic"),
    @XmlEnumValue("entity")
    ENTITY("entity"),
    @XmlEnumValue("candidate")
    CANDIDATE("candidate");
    private final String value;

    AFConfigExtractionTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AFConfigExtractionTypeEnum fromValue(String v) {
        for (AFConfigExtractionTypeEnum c: AFConfigExtractionTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
