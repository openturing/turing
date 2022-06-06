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
 * <p>Java class for AFConfigRuleUnitType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFConfigRuleUnitType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="authorityName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="termName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="minWeight" type="{http://www.w3.org/2001/XMLSchema}double" minOccurs="0"/>
 *         &lt;element name="maxWeight" type="{http://www.w3.org/2001/XMLSchema}double" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AFConfigRuleUnitType", propOrder = {
    "authorityName",
    "termName",
    "minWeight",
    "maxWeight"
})
public class AFConfigRuleUnitType {

    @XmlElement(required = true)
    protected String authorityName;
    protected String termName;
    protected Double minWeight;
    protected Double maxWeight;

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
     *     {@link String }
     *     
     */
    public String getTermName() {
        return termName;
    }

    /**
     * Sets the value of the termName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTermName(String value) {
        this.termName = value;
    }

    /**
     * Gets the value of the minWeight property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getMinWeight() {
        return minWeight;
    }

    /**
     * Sets the value of the minWeight property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setMinWeight(Double value) {
        this.minWeight = value;
    }

    /**
     * Gets the value of the maxWeight property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getMaxWeight() {
        return maxWeight;
    }

    /**
     * Sets the value of the maxWeight property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setMaxWeight(Double value) {
        this.maxWeight = value;
    }

}
