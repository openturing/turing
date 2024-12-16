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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFConfigRuleActionMergeType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFConfigRuleActionMergeType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="text" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleActionMergeRangeType"/>
 *         &lt;element name="authorityName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="termName" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleActionMergeRangeType"/>
 *         &lt;element name="weight" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AFConfigRuleActionMergeType", propOrder = {
    "text",
    "authorityName",
    "termName",
    "weight"
})
public class AFConfigRuleActionMergeType {

    @XmlElement(required = true)
    protected AFConfigRuleActionMergeRangeType text;
    @XmlElement(required = true)
    protected String authorityName;
    @XmlElement(required = true)
    protected AFConfigRuleActionMergeRangeType termName;
    @XmlElement(required = true)
    protected String weight;

    /**
     * Gets the value of the text property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public AFConfigRuleActionMergeRangeType getText() {
        return text;
    }

    /**
     * Sets the value of the text property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public void setText(AFConfigRuleActionMergeRangeType value) {
        this.text = value;
    }

    /**
     * Gets the value of the authorityName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAuthorityName() {
        return authorityName;
    }

    /**
     * Sets the value of the authorityName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAuthorityName(String value) {
        this.authorityName = value;
    }

    /**
     * Gets the value of the termName property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public AFConfigRuleActionMergeRangeType getTermName() {
        return termName;
    }

    /**
     * Sets the value of the termName property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleActionMergeRangeType }
     *     
     */
    public void setTermName(AFConfigRuleActionMergeRangeType value) {
        this.termName = value;
    }

    /**
     * Gets the value of the weight property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getWeight() {
        return weight;
    }

    /**
     * Sets the value of the weight property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setWeight(String value) {
        this.weight = value;
    }

}
