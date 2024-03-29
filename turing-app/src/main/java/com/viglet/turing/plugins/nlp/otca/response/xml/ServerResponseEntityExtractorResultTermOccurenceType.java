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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;


/**
 * <p>Java class for ServerResponseEntityExtractorResultTermOccurenceType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseEntityExtractorResultTermOccurenceType">
 *   &lt;simpleContent>
 *     &lt;extension base="&lt;http://www.w3.org/2001/XMLSchema>string">
 *       &lt;attribute name="Id" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="Position" use="required" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="Length" use="required" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="Candidate" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="Subjectivity" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="SubjectivityConfidenceScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="SubjectivityScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="Tone" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="ToneConfidenceScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="PositiveToneScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="NegativeToneScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="SentenceBeginIndex" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="SentenceEndIndex" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="Sentence" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/extension>
 *   &lt;/simpleContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseEntityExtractorResultTermOccurenceType", propOrder = {
    "value"
})
public class ServerResponseEntityExtractorResultTermOccurenceType {

    @XmlValue
    protected String value;
    @XmlAttribute(name = "Id")
    protected String id;
    @XmlAttribute(name = "Position", required = true)
    protected int position;
    @XmlAttribute(name = "Length", required = true)
    protected int length;
    @XmlAttribute(name = "Candidate")
    protected Integer candidate;
    @XmlAttribute(name = "Subjectivity")
    protected String subjectivity;
    @XmlAttribute(name = "SubjectivityConfidenceScore")
    protected Double subjectivityConfidenceScore;
    @XmlAttribute(name = "SubjectivityScore")
    protected Double subjectivityScore;
    @XmlAttribute(name = "Tone")
    protected String tone;
    @XmlAttribute(name = "ToneConfidenceScore")
    protected Double toneConfidenceScore;
    @XmlAttribute(name = "PositiveToneScore")
    protected Double positiveToneScore;
    @XmlAttribute(name = "NegativeToneScore")
    protected Double negativeToneScore;
    @XmlAttribute(name = "SentenceBeginIndex")
    protected Integer sentenceBeginIndex;
    @XmlAttribute(name = "SentenceEndIndex")
    protected Integer sentenceEndIndex;
    @XmlAttribute(name = "Sentence")
    protected String sentence;

    /**
     * Gets the value of the value property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of the value property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setValue(String value) {
        this.value = value;
    }

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
     * Gets the value of the position property.
     * 
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets the value of the position property.
     * 
     */
    public void setPosition(int value) {
        this.position = value;
    }

    /**
     * Gets the value of the length property.
     * 
     */
    public int getLength() {
        return length;
    }

    /**
     * Sets the value of the length property.
     * 
     */
    public void setLength(int value) {
        this.length = value;
    }

    /**
     * Gets the value of the candidate property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getCandidate() {
        return candidate;
    }

    /**
     * Sets the value of the candidate property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setCandidate(Integer value) {
        this.candidate = value;
    }

    /**
     * Gets the value of the subjectivity property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSubjectivity() {
        return subjectivity;
    }

    /**
     * Sets the value of the subjectivity property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSubjectivity(String value) {
        this.subjectivity = value;
    }

    /**
     * Gets the value of the subjectivityConfidenceScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getSubjectivityConfidenceScore() {
        return subjectivityConfidenceScore;
    }

    /**
     * Sets the value of the subjectivityConfidenceScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setSubjectivityConfidenceScore(Double value) {
        this.subjectivityConfidenceScore = value;
    }

    /**
     * Gets the value of the subjectivityScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getSubjectivityScore() {
        return subjectivityScore;
    }

    /**
     * Sets the value of the subjectivityScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setSubjectivityScore(Double value) {
        this.subjectivityScore = value;
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

    /**
     * Gets the value of the toneConfidenceScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getToneConfidenceScore() {
        return toneConfidenceScore;
    }

    /**
     * Sets the value of the toneConfidenceScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setToneConfidenceScore(Double value) {
        this.toneConfidenceScore = value;
    }

    /**
     * Gets the value of the positiveToneScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getPositiveToneScore() {
        return positiveToneScore;
    }

    /**
     * Sets the value of the positiveToneScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setPositiveToneScore(Double value) {
        this.positiveToneScore = value;
    }

    /**
     * Gets the value of the negativeToneScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getNegativeToneScore() {
        return negativeToneScore;
    }

    /**
     * Sets the value of the negativeToneScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setNegativeToneScore(Double value) {
        this.negativeToneScore = value;
    }

    /**
     * Gets the value of the sentenceBeginIndex property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getSentenceBeginIndex() {
        return sentenceBeginIndex;
    }

    /**
     * Sets the value of the sentenceBeginIndex property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setSentenceBeginIndex(Integer value) {
        this.sentenceBeginIndex = value;
    }

    /**
     * Gets the value of the sentenceEndIndex property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getSentenceEndIndex() {
        return sentenceEndIndex;
    }

    /**
     * Sets the value of the sentenceEndIndex property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setSentenceEndIndex(Integer value) {
        this.sentenceEndIndex = value;
    }

    /**
     * Gets the value of the sentence property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSentence() {
        return sentence;
    }

    /**
     * Sets the value of the sentence property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSentence(String value) {
        this.sentence = value;
    }

}
