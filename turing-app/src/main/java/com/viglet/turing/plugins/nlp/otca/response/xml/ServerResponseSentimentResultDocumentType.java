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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseSentimentResultDocumentType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseSentimentResultDocumentType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Subjectivity" type="{}ServerResponseSentimentResultDocumentSubjectivityType" minOccurs="0"/>
 *         &lt;element name="PositiveTone" type="{}ServerResponseSentimentResultDocumentToneType" minOccurs="0"/>
 *         &lt;element name="NegativeTone" type="{}ServerResponseSentimentResultDocumentToneType" minOccurs="0"/>
 *         &lt;element name="Tone" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseSentimentResultDocumentType", propOrder = {
    "subjectivity",
    "positiveTone",
    "negativeTone",
    "tone"
})
public class ServerResponseSentimentResultDocumentType {

    @XmlElement(name = "Subjectivity")
    protected ServerResponseSentimentResultDocumentSubjectivityType subjectivity;
    @XmlElement(name = "PositiveTone")
    protected ServerResponseSentimentResultDocumentToneType positiveTone;
    @XmlElement(name = "NegativeTone")
    protected ServerResponseSentimentResultDocumentToneType negativeTone;
    @XmlElement(name = "Tone")
    protected String tone;

    /**
     * Gets the value of the subjectivity property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultDocumentSubjectivityType }
     *     
     */
    public ServerResponseSentimentResultDocumentSubjectivityType getSubjectivity() {
        return subjectivity;
    }

    /**
     * Sets the value of the subjectivity property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultDocumentSubjectivityType }
     *     
     */
    public void setSubjectivity(ServerResponseSentimentResultDocumentSubjectivityType value) {
        this.subjectivity = value;
    }

    /**
     * Gets the value of the positiveTone property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultDocumentToneType }
     *     
     */
    public ServerResponseSentimentResultDocumentToneType getPositiveTone() {
        return positiveTone;
    }

    /**
     * Sets the value of the positiveTone property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultDocumentToneType }
     *     
     */
    public void setPositiveTone(ServerResponseSentimentResultDocumentToneType value) {
        this.positiveTone = value;
    }

    /**
     * Gets the value of the negativeTone property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultDocumentToneType }
     *     
     */
    public ServerResponseSentimentResultDocumentToneType getNegativeTone() {
        return negativeTone;
    }

    /**
     * Sets the value of the negativeTone property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultDocumentToneType }
     *     
     */
    public void setNegativeTone(ServerResponseSentimentResultDocumentToneType value) {
        this.negativeTone = value;
    }

    /**
     * Gets the value of the tone property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTone() {
        return tone;
    }

    /**
     * Sets the value of the tone property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTone(String value) {
        this.tone = value;
    }

}
