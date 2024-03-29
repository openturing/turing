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
 * <p>Java class for ServerResponseEntityExtractorResultTermType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseEntityExtractorResultTermType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="nfinderNormalized" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="ClientNormalized" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="Id" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="MainTerm" type="{}ServerResponseEntityExtractorResultTermMaintermType" minOccurs="0"/>
 *         &lt;element name="Subterms" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="Subterm" type="{}ServerResponseEntityExtractorResultTermOccurenceType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="Hierarchy" type="{}ServerResponseEntityExtractorResultTermHierarchyType" minOccurs="0"/>
 *         &lt;element name="Homonyms" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="Homonym" type="{}ServerResponseEntityExtractorResultTermHomonymType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *       &lt;attribute name="CartridgeID" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="ConfidenceScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="RelevancyScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="Frequency" type="{http://www.w3.org/2001/XMLSchema}int" />
 *       &lt;attribute name="Subjectivity" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="SubjectivityConfidenceScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="SubjectivityScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="SubjectivityDistribution" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="Tone" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="ToneConfidenceScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="PositiveToneScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="PositiveToneDistribution" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="NegativeToneScore" type="{http://www.w3.org/2001/XMLSchema}double" />
 *       &lt;attribute name="NegativeToneDistribution" type="{http://www.w3.org/2001/XMLSchema}double" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseEntityExtractorResultTermType", propOrder = {
    "nfinderNormalized",
    "clientNormalized",
    "id",
    "mainTerm",
    "subterms",
    "hierarchy",
    "homonyms"
})
public class ServerResponseEntityExtractorResultTermType {

    protected String nfinderNormalized;
    @XmlElement(name = "ClientNormalized")
    protected String clientNormalized;
    @XmlElement(name = "Id")
    protected String id;
    @XmlElement(name = "MainTerm")
    protected ServerResponseEntityExtractorResultTermMaintermType mainTerm;
    @XmlElement(name = "Subterms")
    protected ServerResponseEntityExtractorResultTermType.Subterms subterms;
    @XmlElement(name = "Hierarchy")
    protected ServerResponseEntityExtractorResultTermHierarchyType hierarchy;
    @XmlElement(name = "Homonyms")
    protected ServerResponseEntityExtractorResultTermType.Homonyms homonyms;
    @XmlAttribute(name = "CartridgeID")
    protected String cartridgeID;
    @XmlAttribute(name = "ConfidenceScore")
    protected Double confidenceScore;
    @XmlAttribute(name = "RelevancyScore")
    protected Double relevancyScore;
    @XmlAttribute(name = "Frequency")
    protected Integer frequency;
    @XmlAttribute(name = "Subjectivity")
    protected String subjectivity;
    @XmlAttribute(name = "SubjectivityConfidenceScore")
    protected Double subjectivityConfidenceScore;
    @XmlAttribute(name = "SubjectivityScore")
    protected Double subjectivityScore;
    @XmlAttribute(name = "SubjectivityDistribution")
    protected Double subjectivityDistribution;
    @XmlAttribute(name = "Tone")
    protected String tone;
    @XmlAttribute(name = "ToneConfidenceScore")
    protected Double toneConfidenceScore;
    @XmlAttribute(name = "PositiveToneScore")
    protected Double positiveToneScore;
    @XmlAttribute(name = "PositiveToneDistribution")
    protected Double positiveToneDistribution;
    @XmlAttribute(name = "NegativeToneScore")
    protected Double negativeToneScore;
    @XmlAttribute(name = "NegativeToneDistribution")
    protected Double negativeToneDistribution;

    /**
     * Gets the value of the nfinderNormalized property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNfinderNormalized() {
        return nfinderNormalized;
    }

    /**
     * Sets the value of the nfinderNormalized property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNfinderNormalized(String value) {
        this.nfinderNormalized = value;
    }

    /**
     * Gets the value of the clientNormalized property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getClientNormalized() {
        return clientNormalized;
    }

    /**
     * Sets the value of the clientNormalized property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setClientNormalized(String value) {
        this.clientNormalized = value;
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
     * Gets the value of the mainTerm property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseEntityExtractorResultTermMaintermType }
     *     
     */
    public ServerResponseEntityExtractorResultTermMaintermType getMainTerm() {
        return mainTerm;
    }

    /**
     * Sets the value of the mainTerm property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseEntityExtractorResultTermMaintermType }
     *     
     */
    public void setMainTerm(ServerResponseEntityExtractorResultTermMaintermType value) {
        this.mainTerm = value;
    }

    /**
     * Gets the value of the subterms property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseEntityExtractorResultTermType.Subterms }
     *     
     */
    public ServerResponseEntityExtractorResultTermType.Subterms getSubterms() {
        return subterms;
    }

    /**
     * Sets the value of the subterms property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseEntityExtractorResultTermType.Subterms }
     *     
     */
    public void setSubterms(ServerResponseEntityExtractorResultTermType.Subterms value) {
        this.subterms = value;
    }

    /**
     * Gets the value of the hierarchy property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseEntityExtractorResultTermHierarchyType }
     *     
     */
    public ServerResponseEntityExtractorResultTermHierarchyType getHierarchy() {
        return hierarchy;
    }

    /**
     * Sets the value of the hierarchy property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseEntityExtractorResultTermHierarchyType }
     *     
     */
    public void setHierarchy(ServerResponseEntityExtractorResultTermHierarchyType value) {
        this.hierarchy = value;
    }

    /**
     * Gets the value of the homonyms property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseEntityExtractorResultTermType.Homonyms }
     *     
     */
    public ServerResponseEntityExtractorResultTermType.Homonyms getHomonyms() {
        return homonyms;
    }

    /**
     * Sets the value of the homonyms property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseEntityExtractorResultTermType.Homonyms }
     *     
     */
    public void setHomonyms(ServerResponseEntityExtractorResultTermType.Homonyms value) {
        this.homonyms = value;
    }

    /**
     * Gets the value of the cartridgeID property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCartridgeID() {
        return cartridgeID;
    }

    /**
     * Sets the value of the cartridgeID property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCartridgeID(String value) {
        this.cartridgeID = value;
    }

    /**
     * Gets the value of the confidenceScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getConfidenceScore() {
        return confidenceScore;
    }

    /**
     * Sets the value of the confidenceScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setConfidenceScore(Double value) {
        this.confidenceScore = value;
    }

    /**
     * Gets the value of the relevancyScore property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getRelevancyScore() {
        return relevancyScore;
    }

    /**
     * Sets the value of the relevancyScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setRelevancyScore(Double value) {
        this.relevancyScore = value;
    }

    /**
     * Gets the value of the frequency property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getFrequency() {
        return frequency;
    }

    /**
     * Sets the value of the frequency property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setFrequency(Integer value) {
        this.frequency = value;
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
     * Gets the value of the subjectivityDistribution property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getSubjectivityDistribution() {
        return subjectivityDistribution;
    }

    /**
     * Sets the value of the subjectivityDistribution property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setSubjectivityDistribution(Double value) {
        this.subjectivityDistribution = value;
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
     * Gets the value of the positiveToneDistribution property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getPositiveToneDistribution() {
        return positiveToneDistribution;
    }

    /**
     * Sets the value of the positiveToneDistribution property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setPositiveToneDistribution(Double value) {
        this.positiveToneDistribution = value;
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
     * Gets the value of the negativeToneDistribution property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getNegativeToneDistribution() {
        return negativeToneDistribution;
    }

    /**
     * Sets the value of the negativeToneDistribution property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setNegativeToneDistribution(Double value) {
        this.negativeToneDistribution = value;
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
     *         &lt;element name="Homonym" type="{}ServerResponseEntityExtractorResultTermHomonymType" maxOccurs="unbounded" minOccurs="0"/>
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
        "homonym"
    })
    public static class Homonyms {

        @XmlElement(name = "Homonym")
        protected List<ServerResponseEntityExtractorResultTermHomonymType> homonym;

        /**
         * Gets the value of the homonym property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the homonym property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getHomonym().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link ServerResponseEntityExtractorResultTermHomonymType }
         * 
         * 
         */
        public List<ServerResponseEntityExtractorResultTermHomonymType> getHomonym() {
            if (homonym == null) {
                homonym = new ArrayList<>();
            }
            return this.homonym;
        }

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
     *         &lt;element name="Subterm" type="{}ServerResponseEntityExtractorResultTermOccurenceType" maxOccurs="unbounded" minOccurs="0"/>
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
        "subterm"
    })
    public static class Subterms {

        @XmlElement(name = "Subterm")
        protected List<ServerResponseEntityExtractorResultTermOccurenceType> subterm;

        /**
         * Gets the value of the subterm property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the subterm property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getSubterm().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link ServerResponseEntityExtractorResultTermOccurenceType }
         * 
         * 
         */
        public List<ServerResponseEntityExtractorResultTermOccurenceType> getSubterm() {
            if (subterm == null) {
                subterm = new ArrayList<>();
            }
            return this.subterm;
        }

    }

}
