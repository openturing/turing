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

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseSentimentResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseSentimentResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="SentenceLevel" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="Sentence" type="{}ServerResponseSentimentResultSentenceType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="DocumentLevel" type="{}ServerResponseSentimentResultDocumentType" minOccurs="0"/>
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
@XmlType(name = "ServerResponseSentimentResultType", propOrder = {
    "sentenceLevel",
    "documentLevel"
})
public class ServerResponseSentimentResultType {

    @XmlElement(name = "SentenceLevel")
    protected ServerResponseSentimentResultType.SentenceLevel sentenceLevel;
    @XmlElement(name = "DocumentLevel")
    protected ServerResponseSentimentResultDocumentType documentLevel;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the sentenceLevel property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultType.SentenceLevel }
     *     
     */
    public ServerResponseSentimentResultType.SentenceLevel getSentenceLevel() {
        return sentenceLevel;
    }

    /**
     * Sets the value of the sentenceLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultType.SentenceLevel }
     *     
     */
    public void setSentenceLevel(ServerResponseSentimentResultType.SentenceLevel value) {
        this.sentenceLevel = value;
    }

    /**
     * Gets the value of the documentLevel property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultDocumentType }
     *     
     */
    public ServerResponseSentimentResultDocumentType getDocumentLevel() {
        return documentLevel;
    }

    /**
     * Sets the value of the documentLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultDocumentType }
     *     
     */
    public void setDocumentLevel(ServerResponseSentimentResultDocumentType value) {
        this.documentLevel = value;
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


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;sequence>
     *         &lt;element name="Sentence" type="{}ServerResponseSentimentResultSentenceType" maxOccurs="unbounded" minOccurs="0"/>
     *       &lt;/sequence>
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "sentence"
    })
    public static class SentenceLevel {

        @XmlElement(name = "Sentence")
        protected List<ServerResponseSentimentResultSentenceType> sentence;

        /**
         * Gets the value of the sentence property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the sentence property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getSentence().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link ServerResponseSentimentResultSentenceType }
         * 
         * 
         */
        public List<ServerResponseSentimentResultSentenceType> getSentence() {
            if (sentence == null) {
                sentence = new ArrayList<>();
            }
            return this.sentence;
        }

    }

}
