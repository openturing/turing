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

package com.viglet.turing.plugins.otca.af.xml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * <p>Java class for AFTermVariationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFTermVariationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{http://dev.nstein.com/common/1.3}name"/>
 *         &lt;element name="weight" type="{http://www.w3.org/2001/XMLSchema}double"/>
 *         &lt;element name="prefix" type="{http://dev.nstein.com/tme-authority-file/4.10}AFTermVariationAffixType" minOccurs="0"/>
 *         &lt;element name="suffix" type="{http://dev.nstein.com/tme-authority-file/4.10}AFTermVariationAffixType" minOccurs="0"/>
 *         &lt;element name="case" type="{http://dev.nstein.com/tme-authority-file/4.10}AFTermVariationCaseEnum" minOccurs="0"/>
 *         &lt;element name="accent" type="{http://dev.nstein.com/tme-authority-file/4.10}AFTermVariationAccentEnum" minOccurs="0"/>
 *         &lt;element name="languages" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="language" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
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
@XmlType(name = "AFTermVariationType", propOrder = {
    "name",
    "weight",
    "prefix",
    "suffix",
    "_case",
    "accent",
    "languages"
})
public class AFTermVariationType {

    @XmlElement(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected String name;
    protected double weight;
    protected AFTermVariationAffixType prefix;
    protected AFTermVariationAffixType suffix;
    @XmlElement(name = "case")
    protected AFTermVariationCaseEnum _case;
    protected AFTermVariationAccentEnum accent;
    protected AFTermVariationType.Languages languages;

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
     * Gets the value of the weight property.
     * 
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Sets the value of the weight property.
     * 
     */
    public void setWeight(double value) {
        this.weight = value;
    }

    /**
     * Gets the value of the prefix property.
     * 
     * @return
     *     possible object is
     *     {@link AFTermVariationAffixType }
     *     
     */
    public AFTermVariationAffixType getPrefix() {
        return prefix;
    }

    /**
     * Sets the value of the prefix property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFTermVariationAffixType }
     *     
     */
    public void setPrefix(AFTermVariationAffixType value) {
        this.prefix = value;
    }

    /**
     * Gets the value of the suffix property.
     * 
     * @return
     *     possible object is
     *     {@link AFTermVariationAffixType }
     *     
     */
    public AFTermVariationAffixType getSuffix() {
        return suffix;
    }

    /**
     * Sets the value of the suffix property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFTermVariationAffixType }
     *     
     */
    public void setSuffix(AFTermVariationAffixType value) {
        this.suffix = value;
    }

    /**
     * Gets the value of the case property.
     * 
     * @return
     *     possible object is
     *     {@link AFTermVariationCaseEnum }
     *     
     */
    public AFTermVariationCaseEnum getCase() {
        return _case;
    }

    /**
     * Sets the value of the case property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFTermVariationCaseEnum }
     *     
     */
    public void setCase(AFTermVariationCaseEnum value) {
        this._case = value;
    }

    /**
     * Gets the value of the accent property.
     * 
     * @return
     *     possible object is
     *     {@link AFTermVariationAccentEnum }
     *     
     */
    public AFTermVariationAccentEnum getAccent() {
        return accent;
    }

    /**
     * Sets the value of the accent property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFTermVariationAccentEnum }
     *     
     */
    public void setAccent(AFTermVariationAccentEnum value) {
        this.accent = value;
    }

    /**
     * Gets the value of the languages property.
     * 
     * @return
     *     possible object is
     *     {@link AFTermVariationType.Languages }
     *     
     */
    public AFTermVariationType.Languages getLanguages() {
        return languages;
    }

    /**
     * Sets the value of the languages property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFTermVariationType.Languages }
     *     
     */
    public void setLanguages(AFTermVariationType.Languages value) {
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
     *         &lt;element name="language" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/>
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

        protected List<String> language;

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
         * {@link String }
         * 
         * 
         */
        public List<String> getLanguage() {
            if (language == null) {
                language = new ArrayList<String>();
            }
            return this.language;
        }

    }

}
