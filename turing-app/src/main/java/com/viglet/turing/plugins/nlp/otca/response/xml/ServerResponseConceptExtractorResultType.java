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

package com.viglet.turing.plugins.nlp.otca.response.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseConceptExtractorResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseConceptExtractorResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="ComplexConcepts" type="{}ServerResponseConceptExtractorResultConceptsType" minOccurs="0"/>
 *         &lt;element name="SimpleConcepts" type="{}ServerResponseConceptExtractorResultConceptsType" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="Name" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseConceptExtractorResultType", propOrder = {
    "complexConcepts",
    "simpleConcepts"
})
public class ServerResponseConceptExtractorResultType {

    @XmlElement(name = "ComplexConcepts")
    protected ServerResponseConceptExtractorResultConceptsType complexConcepts;
    @XmlElement(name = "SimpleConcepts")
    protected ServerResponseConceptExtractorResultConceptsType simpleConcepts;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the complexConcepts property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public ServerResponseConceptExtractorResultConceptsType getComplexConcepts() {
        return complexConcepts;
    }

    /**
     * Sets the value of the complexConcepts property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public void setComplexConcepts(ServerResponseConceptExtractorResultConceptsType value) {
        this.complexConcepts = value;
    }

    /**
     * Gets the value of the simpleConcepts property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public ServerResponseConceptExtractorResultConceptsType getSimpleConcepts() {
        return simpleConcepts;
    }

    /**
     * Sets the value of the simpleConcepts property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public void setSimpleConcepts(ServerResponseConceptExtractorResultConceptsType value) {
        this.simpleConcepts = value;
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
