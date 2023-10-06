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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFConfigRuleActionMergeType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFConfigRuleActionMergeType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="text" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleActionMergeRangeType"/>
 *         &lt;element name="authorityName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="termName" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleActionMergeRangeType"/>
 *         &lt;element name="weight" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AFConfigRuleActionMergeType", propOrder = {
    "text",
    "authorityName",
    "termName",
    "weight"
})
public class AFConfigRuleActionMergeType {

    @XmlElement(required = true)
    protected AFConfigRuleActionMergeRangeType text;
    @XmlElement(required = true)
    protected String authorityName;
    @XmlElement(required = true)
    protected AFConfigRuleActionMergeRangeType termName;
    @XmlElement(required = true)
    protected String weight;

    /**
     * Gets the value of the text property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public AFConfigRuleActionMergeRangeType getText() {
        return text;
    }

    /**
     * Sets the value of the text property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public void setText(AFConfigRuleActionMergeRangeType value) {
        this.text = value;
    }

    /**
     * Gets the value of the authorityName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAuthorityName() {
        return authorityName;
    }

    /**
     * Sets the value of the authorityName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAuthorityName(String value) {
        this.authorityName = value;
    }

    /**
     * Gets the value of the termName property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public AFConfigRuleActionMergeRangeType getTermName() {
        return termName;
    }

    /**
     * Sets the value of the termName property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public void setTermName(AFConfigRuleActionMergeRangeType value) {
        this.termName = value;
    }

    /**
     * Gets the value of the weight property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWeight() {
        return weight;
    }

    /**
     * Sets the value of the weight property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWeight(String value) {
        this.weight = value;
    }

}
