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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseLanguageDetectorResultParagraphType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseLanguageDetectorResultParagraphType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="BeginIndex" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="EndIndex" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="Text" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="Languages" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="Language" type="{}ServerResponseLanguageDetectorResultCandidateType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseLanguageDetectorResultParagraphType", propOrder = {
    "beginIndex",
    "endIndex",
    "text",
    "languages"
})
public class ServerResponseLanguageDetectorResultParagraphType {

    @XmlElement(name = "BeginIndex")
    protected int beginIndex;
    @XmlElement(name = "EndIndex")
    protected int endIndex;
    @XmlElement(name = "Text")
    protected String text;
    @XmlElement(name = "Languages")
    protected ServerResponseLanguageDetectorResultParagraphType.Languages languages;

    /**
     * Gets the value of the beginIndex property.
     * 
     */
    public int getBeginIndex() {
        return beginIndex;
    }

    /**
     * Sets the value of the beginIndex property.
     * 
     */
    public void setBeginIndex(int value) {
        this.beginIndex = value;
    }

    /**
     * Gets the value of the endIndex property.
     * 
     */
    public int getEndIndex() {
        return endIndex;
    }

    /**
     * Sets the value of the endIndex property.
     * 
     */
    public void setEndIndex(int value) {
        this.endIndex = value;
    }

    /**
     * Gets the value of the text property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the value of the text property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setText(String value) {
        this.text = value;
    }

    /**
     * Gets the value of the languages property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseLanguageDetectorResultParagraphType.Languages }
     *     
     */
    public ServerResponseLanguageDetectorResultParagraphType.Languages getLanguages() {
        return languages;
    }

    /**
     * Sets the value of the languages property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseLanguageDetectorResultParagraphType.Languages }
     *     
     */
    public void setLanguages(ServerResponseLanguageDetectorResultParagraphType.Languages value) {
        this.languages = value;
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
     *         &lt;element name="Language" type="{}ServerResponseLanguageDetectorResultCandidateType" maxOccurs="unbounded" minOccurs="0"/>
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
        "language"
    })
    public static class Languages {

        @XmlElement(name = "Language")
        protected List<ServerResponseLanguageDetectorResultCandidateType> language;

        /**
         * Gets the value of the language property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the language property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getLanguage().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link ServerResponseLanguageDetectorResultCandidateType }
         * 
         * 
         */
        public List<ServerResponseLanguageDetectorResultCandidateType> getLanguage() {
            if (language == null) {
                language = new ArrayList<>();
            }
            return this.language;
        }

    }

}
