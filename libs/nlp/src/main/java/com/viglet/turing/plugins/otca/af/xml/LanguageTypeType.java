//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.2-147 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2017.02.21 at 09:58:26 PM BRT 
//


package com.viglet.turing.plugins.otca.af.xml;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LanguageTypeType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="LanguageTypeType">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="A"/>
 *     &lt;enumeration value="C"/>
 *     &lt;enumeration value="E"/>
 *     &lt;enumeration value="H"/>
 *     &lt;enumeration value="L"/>
 *     &lt;enumeration value="S"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "LanguageTypeType", namespace = "http://dev.nstein.com/common/1.3")
@XmlEnum
public enum LanguageTypeType {

    A,
    C,
    E,
    H,
    L,
    S;

    public String value() {
        return name();
    }

    public static LanguageTypeType fromValue(String v) {
        return valueOf(v);
    }

}
