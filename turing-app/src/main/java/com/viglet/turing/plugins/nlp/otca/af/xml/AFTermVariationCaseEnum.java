/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.plugins.nlp.otca.af.xml;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFTermVariationCaseEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="AFTermVariationCaseEnum">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="ci"/>
 *     &lt;enumeration value="cs"/>
 *     &lt;enumeration value="ucs"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "AFTermVariationCaseEnum")
@XmlEnum
public enum AFTermVariationCaseEnum {

    @XmlEnumValue("ci")
    CI("ci"),
    @XmlEnumValue("cs")
    CS("cs"),
    @XmlEnumValue("ucs")
    UCS("ucs");
    private final String value;

    AFTermVariationCaseEnum(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static AFTermVariationCaseEnum fromValue(String v) {
        for (AFTermVariationCaseEnum c: AFTermVariationCaseEnum.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
