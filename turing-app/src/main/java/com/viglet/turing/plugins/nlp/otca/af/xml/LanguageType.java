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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LanguageType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LanguageType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="part2B" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="part2T" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="part1" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="scope" type="{http://dev.nstein.com/common/1.3}LanguageScopeType"/>
 *         &lt;element name="type" type="{http://dev.nstein.com/common/1.3}LanguageTypeType"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LanguageType", namespace = "http://dev.nstein.com/common/1.3", propOrder = {
    "id",
    "part2B",
    "part2T",
    "part1",
    "scope",
    "type",
    "name"
})
public class LanguageType {

    @XmlElement(required = true)
    protected String id;
    protected String part2B;
    protected String part2T;
    protected String part1;
    @XmlElement(required = true)
    protected LanguageScopeType scope;
    @XmlElement(required = true)
    protected LanguageTypeType type;
    @XmlElement(required = true)
    protected String name;

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Gets the value of the part2B property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPart2B() {
        return part2B;
    }

    /**
     * Sets the value of the part2B property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPart2B(String value) {
        this.part2B = value;
    }

    /**
     * Gets the value of the part2T property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPart2T() {
        return part2T;
    }

    /**
     * Sets the value of the part2T property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPart2T(String value) {
        this.part2T = value;
    }

    /**
     * Gets the value of the part1 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPart1() {
        return part1;
    }

    /**
     * Sets the value of the part1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPart1(String value) {
        this.part1 = value;
    }

    /**
     * Gets the value of the scope property.
     * 
     * @return
     *     possible object is
     *     {@link LanguageScopeType }
     *     
     */
    public LanguageScopeType getScope() {
        return scope;
    }

    /**
     * Sets the value of the scope property.
     * 
     * @param value
     *     allowed object is
     *     {@link LanguageScopeType }
     *     
     */
    public void setScope(LanguageScopeType value) {
        this.scope = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link LanguageTypeType }
     *     
     */
    public LanguageTypeType getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link LanguageTypeType }
     *     
     */
    public void setType(LanguageTypeType value) {
        this.type = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

}
