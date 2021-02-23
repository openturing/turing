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

package com.viglet.turing.plugins.otca.response.xml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseSimilarityResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseSimilarityResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element name="IsTextIndexed" type="{}ServerResponseEntitySimilarityMethodContainsMethodType"/>
 *           &lt;element name="Compare" type="{}ServerResponseEntitySimilarityMethodCompareMethodType"/>
 *           &lt;element name="GetAllIds" type="{}ServerResponseEntitySimilarityMethodListMethodType"/>
 *           &lt;element name="GetStats" type="{}ServerResponseEntitySimilarityMethodSizeMethodType"/>
 *         &lt;/choice>
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
@XmlType(name = "ServerResponseSimilarityResultType", propOrder = {
    "isTextIndexedOrCompareOrGetAllIds"
})
public class ServerResponseSimilarityResultType {

    @XmlElements({
        @XmlElement(name = "IsTextIndexed", type = ServerResponseEntitySimilarityMethodContainsMethodType.class),
        @XmlElement(name = "GetAllIds", type = ServerResponseEntitySimilarityMethodListMethodType.class),
        @XmlElement(name = "GetStats", type = ServerResponseEntitySimilarityMethodSizeMethodType.class),
        @XmlElement(name = "Compare", type = ServerResponseEntitySimilarityMethodCompareMethodType.class)
    })
    protected List<Object> isTextIndexedOrCompareOrGetAllIds;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the isTextIndexedOrCompareOrGetAllIds property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the isTextIndexedOrCompareOrGetAllIds property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getIsTextIndexedOrCompareOrGetAllIds().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServerResponseEntitySimilarityMethodContainsMethodType }
     * {@link ServerResponseEntitySimilarityMethodListMethodType }
     * {@link ServerResponseEntitySimilarityMethodSizeMethodType }
     * {@link ServerResponseEntitySimilarityMethodCompareMethodType }
     * 
     * 
     */
    public List<Object> getIsTextIndexedOrCompareOrGetAllIds() {
        if (isTextIndexedOrCompareOrGetAllIds == null) {
            isTextIndexedOrCompareOrGetAllIds = new ArrayList<Object>();
        }
        return this.isTextIndexedOrCompareOrGetAllIds;
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
