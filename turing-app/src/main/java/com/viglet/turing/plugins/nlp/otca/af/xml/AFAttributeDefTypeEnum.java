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
 * <p>Java class for AFAttributeDefTypeEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AFAttributeDefTypeEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="text"/>
 *     &lt;enumeration value="textarea"/>
 *     &lt;enumeration value="radio"/>
 *     &lt;enumeration value="checkbox"/>
 *     &lt;enumeration value="select"/>
 *     &lt;enumeration value="list"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AFAttributeDefTypeEnum")
@XmlEnum
public enum AFAttributeDefTypeEnum {

    @XmlEnumValue("text")
    TEXT("text"),
    @XmlEnumValue("textarea")
    TEXTAREA("textarea"),
    @XmlEnumValue("radio")
    RADIO("radio"),
    @XmlEnumValue("checkbox")
    CHECKBOX("checkbox"),
    @XmlEnumValue("select")
    SELECT("select"),
    @XmlEnumValue("list")
    LIST("list");
    private final String value;

    AFAttributeDefTypeEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AFAttributeDefTypeEnum fromValue(String v) {
        for (AFAttributeDefTypeEnum c: AFAttributeDefTypeEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
