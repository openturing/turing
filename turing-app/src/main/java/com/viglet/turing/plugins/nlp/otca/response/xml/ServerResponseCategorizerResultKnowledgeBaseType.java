/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.plugins.nlp.otca.response.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseCategorizerResultKnowledgeBaseType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseCategorizerResultKnowledgeBaseType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="KBid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="RelevancyScore" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="Categories" type="{}ServerResponseCategorizerResultCategoriesType" minOccurs="0"/>
 *         &lt;element name="RejectedCategories" type="{}ServerResponseCategorizerResultRejectedCategoriesType" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseCategorizerResultKnowledgeBaseType", propOrder = {
    "kBid",
    "relevancyScore",
    "categories",
    "rejectedCategories"
})
public class ServerResponseCategorizerResultKnowledgeBaseType {

    @XmlElement(name = "KBid")
    protected String kBid;
    @XmlElement(name = "RelevancyScore")
    protected Integer relevancyScore;
    @XmlElement(name = "Categories")
    protected ServerResponseCategorizerResultCategoriesType categories;
    @XmlElement(name = "RejectedCategories")
    protected ServerResponseCategorizerResultRejectedCategoriesType rejectedCategories;

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

}
