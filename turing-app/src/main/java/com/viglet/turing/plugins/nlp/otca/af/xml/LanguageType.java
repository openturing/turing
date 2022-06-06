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
