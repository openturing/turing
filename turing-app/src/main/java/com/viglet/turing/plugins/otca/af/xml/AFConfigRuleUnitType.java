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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AFConfigRuleUnitType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFConfigRuleUnitType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="authorityName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="termName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *         &lt;element name="minWeight" type="{http://www.w3.org/2001/XMLSchema}double" minOccurs="0"/>
 *         &lt;element name="maxWeight" type="{http://www.w3.org/2001/XMLSchema}double" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AFConfigRuleUnitType", propOrder = {
    "authorityName",
    "termName",
    "minWeight",
    "maxWeight"
})
public class AFConfigRuleUnitType {

    @XmlElement(required = true)
    protected String authorityName;
    protected String termName;
    protected Double minWeight;
    protected Double maxWeight;

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
     *     {@link String }
     *     
     */
    public String getTermName() {
        return termName;
    }

    /**
     * Sets the value of the termName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTermName(String value) {
        this.termName = value;
    }

    /**
     * Gets the value of the minWeight property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getMinWeight() {
        return minWeight;
    }

    /**
     * Sets the value of the minWeight property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setMinWeight(Double value) {
        this.minWeight = value;
    }

    /**
     * Gets the value of the maxWeight property.
     * 
     * @return
     *     possible object is
     *     {@link Double }
     *     
     */
    public Double getMaxWeight() {
        return maxWeight;
    }

    /**
     * Sets the value of the maxWeight property.
     * 
     * @param value
     *     allowed object is
     *     {@link Double }
     *     
     */
    public void setMaxWeight(Double value) {
        this.maxWeight = value;
    }

}
