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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseCategorizerResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseCategorizerResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="KBid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="RelevancyScore" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="Categories" type="{}ServerResponseCategorizerResultCategoriesType" minOccurs="0"/>
 *         &lt;element name="RejectedCategories" type="{}ServerResponseCategorizerResultRejectedCategoriesType" minOccurs="0"/>
 *         &lt;element name="KnowledgeBase" type="{}ServerResponseCategorizerResultKnowledgeBaseType" maxOccurs="unbounded" minOccurs="0"/>
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
@XmlType(name = "ServerResponseCategorizerResultType", propOrder = {
    "kBid",
    "relevancyScore",
    "categories",
    "rejectedCategories",
    "knowledgeBase"
})
public class ServerResponseCategorizerResultType {

    @XmlElement(name = "KBid")
    protected String kBid;
    @XmlElement(name = "RelevancyScore")
    protected Integer relevancyScore;
    @XmlElement(name = "Categories")
    protected ServerResponseCategorizerResultCategoriesType categories;
    @XmlElement(name = "RejectedCategories")
    protected ServerResponseCategorizerResultRejectedCategoriesType rejectedCategories;
    @XmlElement(name = "KnowledgeBase")
    protected List<ServerResponseCategorizerResultKnowledgeBaseType> knowledgeBase;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the kBid property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getKBid() {
        return kBid;
    }

    /**
     * Sets the value of the kBid property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setKBid(String value) {
        this.kBid = value;
    }

    /**
     * Gets the value of the relevancyScore property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getRelevancyScore() {
        return relevancyScore;
    }

    /**
     * Sets the value of the relevancyScore property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setRelevancyScore(Integer value) {
        this.relevancyScore = value;
    }

    /**
     * Gets the value of the categories property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseCategorizerResultCategoriesType }
     *     
     */
    public ServerResponseCategorizerResultCategoriesType getCategories() {
        return categories;
    }

    /**
     * Sets the value of the categories property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseCategorizerResultCategoriesType }
     *     
     */
    public void setCategories(ServerResponseCategorizerResultCategoriesType value) {
        this.categories = value;
    }

    /**
     * Gets the value of the rejectedCategories property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseCategorizerResultRejectedCategoriesType }
     *     
     */
    public ServerResponseCategorizerResultRejectedCategoriesType getRejectedCategories() {
        return rejectedCategories;
    }

    /**
     * Sets the value of the rejectedCategories property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseCategorizerResultRejectedCategoriesType }
     *     
     */
    public void setRejectedCategories(ServerResponseCategorizerResultRejectedCategoriesType value) {
        this.rejectedCategories = value;
    }

    /**
     * Gets the value of the knowledgeBase property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the knowledgeBase property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getKnowledgeBase().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServerResponseCategorizerResultKnowledgeBaseType }
     * 
     * 
     */
    public List<ServerResponseCategorizerResultKnowledgeBaseType> getKnowledgeBase() {
        if (knowledgeBase == null) {
            knowledgeBase = new ArrayList<>();
        }
        return this.knowledgeBase;
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
