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
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SearchTypeType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SearchTypeType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="label" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="wordPattern" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="contiguityPattern" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="separatorPattern" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="separatorMaxLength" type="{http://www.w3.org/2001/XMLSchema}unsignedByte"/>
 *         &lt;element name="resultAnyPattern" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="resultSeparatorPattern" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SearchTypeType", namespace = "http://dev.nstein.com/common/1.3", propOrder = {
    "name",
    "label",
    "wordPattern",
    "contiguityPattern",
    "separatorPattern",
    "separatorMaxLength",
    "resultAnyPattern",
    "resultSeparatorPattern"
})
public class SearchTypeType {

    @XmlElement(required = true)
    protected String name;
    @XmlElement(required = true)
    protected String label;
    @XmlElement(required = true)
    protected String wordPattern;
    @XmlElement(required = true)
    protected String contiguityPattern;
    @XmlElement(required = true)
    protected String separatorPattern;
    @XmlSchemaType(name = "unsignedByte")
    protected short separatorMaxLength;
    @XmlElement(required = true)
    protected String resultAnyPattern;
    @XmlElement(required = true)
    protected String resultSeparatorPattern;

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

    /**
     * Gets the value of the label property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the value of the label property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLabel(String value) {
        this.label = value;
    }

    /**
     * Gets the value of the wordPattern property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWordPattern() {
        return wordPattern;
    }

    /**
     * Sets the value of the wordPattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWordPattern(String value) {
        this.wordPattern = value;
    }

    /**
     * Gets the value of the contiguityPattern property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getContiguityPattern() {
        return contiguityPattern;
    }

    /**
     * Sets the value of the contiguityPattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setContiguityPattern(String value) {
        this.contiguityPattern = value;
    }

    /**
     * Gets the value of the separatorPattern property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSeparatorPattern() {
        return separatorPattern;
    }

    /**
     * Sets the value of the separatorPattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSeparatorPattern(String value) {
        this.separatorPattern = value;
    }

    /**
     * Gets the value of the separatorMaxLength property.
     * 
     */
    public short getSeparatorMaxLength() {
        return separatorMaxLength;
    }

    /**
     * Sets the value of the separatorMaxLength property.
     * 
     */
    public void setSeparatorMaxLength(short value) {
        this.separatorMaxLength = value;
    }

    /**
     * Gets the value of the resultAnyPattern property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResultAnyPattern() {
        return resultAnyPattern;
    }

    /**
     * Sets the value of the resultAnyPattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResultAnyPattern(String value) {
        this.resultAnyPattern = value;
    }

    /**
     * Gets the value of the resultSeparatorPattern property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getResultSeparatorPattern() {
        return resultSeparatorPattern;
    }

    /**
     * Sets the value of the resultSeparatorPattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setResultSeparatorPattern(String value) {
        this.resultSeparatorPattern = value;
    }

}
