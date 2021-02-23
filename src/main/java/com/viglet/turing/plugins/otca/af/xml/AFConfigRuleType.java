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


/**
 * <p>Java class for AFConfigRuleType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AFConfigRuleType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="units">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="unit" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleUnitType" maxOccurs="unbounded"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;choice>
 *           &lt;element name="merge" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleActionMergeType"/>
 *           &lt;element name="delete" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleActionDeleteType"/>
 *         &lt;/choice>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AFConfigRuleType", propOrder = {
    "units",
    "merge",
    "delete"
})
public class AFConfigRuleType {

    @XmlElement(required = true)
    protected AFConfigRuleType.Units units;
    protected AFConfigRuleActionMergeType merge;
    protected AFConfigRuleActionDeleteType delete;

    /**
     * Gets the value of the units property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleType.Units }
     *     
     */
    public AFConfigRuleType.Units getUnits() {
        return units;
    }

    /**
     * Sets the value of the units property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleType.Units }
     *     
     */
    public void setUnits(AFConfigRuleType.Units value) {
        this.units = value;
    }

    /**
     * Gets the value of the merge property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleActionMergeType }
     *     
     */
    public AFConfigRuleActionMergeType getMerge() {
        return merge;
    }

    /**
     * Sets the value of the merge property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleActionMergeType }
     *     
     */
    public void setMerge(AFConfigRuleActionMergeType value) {
        this.merge = value;
    }

    /**
     * Gets the value of the delete property.
     * 
     * @return
     *     possible object is
     *     {@link AFConfigRuleActionDeleteType }
     *     
     */
    public AFConfigRuleActionDeleteType getDelete() {
        return delete;
    }

    /**
     * Sets the value of the delete property.
     * 
     * @param value
     *     allowed object is
     *     {@link AFConfigRuleActionDeleteType }
     *     
     */
    public void setDelete(AFConfigRuleActionDeleteType value) {
        this.delete = value;
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
     *         &lt;element name="unit" type="{http://dev.nstein.com/tme-authority-file/4.10}AFConfigRuleUnitType" maxOccurs="unbounded"/>
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
        "unit"
    })
    public static class Units {

        @XmlElement(required = true)
        protected List<AFConfigRuleUnitType> unit;

        /**
         * Gets the value of the unit property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the unit property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getUnit().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link AFConfigRuleUnitType }
         * 
         * 
         */
        public List<AFConfigRuleUnitType> getUnit() {
            if (unit == null) {
                unit = new ArrayList<AFConfigRuleUnitType>();
            }
            return this.unit;
        }

    }

}
