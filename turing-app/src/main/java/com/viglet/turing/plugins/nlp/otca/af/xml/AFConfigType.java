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

package com.viglet.turing.plugins.nlp.otca.af.xml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFConfigType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFConfigType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="path" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="cacheSize" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="commitDelay" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="poolSize" type="{http://www.w3.org/2001/XMLSchema}unsignedInt" minOccurs="0"/>
 *         &lt;element name="relationsMaxDepth" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="batchSize" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/>
 *         &lt;element name="skipVariationValidation" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/>
 *         &lt;element name="searchTypes" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="searchType" type="{http://dev.nstein.com/common/1.3}SearchTypeType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="parameters">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="parameter" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigParameterType" maxOccurs="unbounded"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="contiguityRules" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="rule" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="disambiguationRules" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="rule" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleType" maxOccurs="unbounded" minOccurs="0"/>
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
@XmlType(name = "AFConfigType", propOrder = {
    "path",
    "cacheSize",
    "commitDelay",
    "poolSize",
    "relationsMaxDepth",
    "batchSize",
    "skipVariationValidation",
    "searchTypes",
    "parameters",
    "contiguityRules",
    "disambiguationRules"
})
public class AFConfigType {

    @XmlElement(required = true)
    protected String path;
    @XmlElement(required = true)
    protected String cacheSize;
    protected String commitDelay;
    @XmlSchemaType(name = "unsignedInt")
    protected Long poolSize;
    protected Integer relationsMaxDepth;
    protected Integer batchSize;
    protected Boolean skipVariationValidation;
    protected AFConfigType.SearchTypes searchTypes;
    @XmlElement(required = true)
    protected AFConfigType.Parameters parameters;
    protected AFConfigType.ContiguityRules contiguityRules;
    protected AFConfigType.DisambiguationRules disambiguationRules;

    /**
     * Gets the value of the path property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the value of the path property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPath(String value) {
        this.path = value;
    }

    /**
     * Gets the value of the cacheSize property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCacheSize() {
        return cacheSize;
    }

    /**
     * Sets the value of the cacheSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCacheSize(String value) {
        this.cacheSize = value;
    }

    /**
     * Gets the value of the commitDelay property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getCommitDelay() {
        return commitDelay;
    }

    /**
     * Sets the value of the commitDelay property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setCommitDelay(String value) {
        this.commitDelay = value;
    }

    /**
     * Gets the value of the poolSize property.
     * 
     * @return
     *     possible object is
     *     {@link Long }
     *     
     */
    public Long getPoolSize() {
        return poolSize;
    }

    /**
     * Sets the value of the poolSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Long }
     *     
     */
    public void setPoolSize(Long value) {
        this.poolSize = value;
    }

    /**
     * Gets the value of the relationsMaxDepth property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getRelationsMaxDepth() {
        return relationsMaxDepth;
    }

    /**
     * Sets the value of the relationsMaxDepth property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setRelationsMaxDepth(Integer value) {
        this.relationsMaxDepth = value;
    }

    /**
     * Gets the value of the batchSize property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the value of the batchSize property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setBatchSize(Integer value) {
        this.batchSize = value;
    }

    /**
     * Gets the value of the skipVariationValidation property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isSkipVariationValidation() {
        return skipVariationValidation;
    }

    /**
     * Sets the value of the skipVariationValidation property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setSkipVariationValidation(Boolean value) {
        this.skipVariationValidation = value;
    }

    /**
     * Gets the value of the searchTypes property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigType.SearchTypes }
     *     
     */
    public AFConfigType.SearchTypes getSearchTypes() {
        return searchTypes;
    }

    /**
     * Sets the value of the searchTypes property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigType.SearchTypes }
     *     
     */
    public void setSearchTypes(AFConfigType.SearchTypes value) {
        this.searchTypes = value;
    }

    /**
     * Gets the value of the parameters property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigType.Parameters }
     *     
     */
    public AFConfigType.Parameters getParameters() {
        return parameters;
    }

    /**
     * Sets the value of the parameters property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigType.Parameters }
     *     
     */
    public void setParameters(AFConfigType.Parameters value) {
        this.parameters = value;
    }

    /**
     * Gets the value of the contiguityRules property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigType.ContiguityRules }
     *     
     */
    public AFConfigType.ContiguityRules getContiguityRules() {
        return contiguityRules;
    }

    /**
     * Sets the value of the contiguityRules property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigType.ContiguityRules }
     *     
     */
    public void setContiguityRules(AFConfigType.ContiguityRules value) {
        this.contiguityRules = value;
    }

    /**
     * Gets the value of the disambiguationRules property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigType.DisambiguationRules }
     *     
     */
    public AFConfigType.DisambiguationRules getDisambiguationRules() {
        return disambiguationRules;
    }

    /**
     * Sets the value of the disambiguationRules property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigType.DisambiguationRules }
     *     
     */
    public void setDisambiguationRules(AFConfigType.DisambiguationRules value) {
        this.disambiguationRules = value;
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
     *         &lt;element name="rule" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleType" maxOccurs="unbounded" minOccurs="0"/>
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
        "rule"
    })
    public static class ContiguityRules {

        protected List<AFConfigRuleType> rule;

        /**
         * Gets the value of the rule property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the rule property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getRule().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link AFConfigRuleType }
         * 
         * 
         */
        public List<AFConfigRuleType> getRule() {
            if (rule == null) {
                rule = new ArrayList<AFConfigRuleType>();
            }
            return this.rule;
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
     *         &lt;element name="rule" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleType" maxOccurs="unbounded" minOccurs="0"/>
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
        "rule"
    })
    public static class DisambiguationRules {

        protected List<AFConfigRuleType> rule;

        /**
         * Gets the value of the rule property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the rule property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getRule().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link AFConfigRuleType }
         * 
         * 
         */
        public List<AFConfigRuleType> getRule() {
            if (rule == null) {
                rule = new ArrayList<AFConfigRuleType>();
            }
            return this.rule;
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
     *         &lt;element name="parameter" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigParameterType" maxOccurs="unbounded"/>
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
        "parameter"
    })
    public static class Parameters {

        @XmlElement(required = true)
        protected List<AFConfigParameterType> parameter;

        /**
         * Gets the value of the parameter property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the parameter property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getParameter().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link AFConfigParameterType }
         * 
         * 
         */
        public List<AFConfigParameterType> getParameter() {
            if (parameter == null) {
                parameter = new ArrayList<AFConfigParameterType>();
            }
            return this.parameter;
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
     *         &lt;element name="searchType" type="{http://dev.nstein.com/common/1.3}SearchTypeType" maxOccurs="unbounded" minOccurs="0"/>
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
        "searchType"
    })
    public static class SearchTypes {

        protected List<SearchTypeType> searchType;

        /**
         * Gets the value of the searchType property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the searchType property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getSearchType().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link SearchTypeType }
         * 
         * 
         */
        public List<SearchTypeType> getSearchType() {
            if (searchType == null) {
                searchType = new ArrayList<SearchTypeType>();
            }
            return this.searchType;
        }

    }

}
