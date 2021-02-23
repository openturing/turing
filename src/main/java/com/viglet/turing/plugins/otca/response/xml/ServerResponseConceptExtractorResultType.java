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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseConceptExtractorResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseConceptExtractorResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="ComplexConcepts" type="{}ServerResponseConceptExtractorResultConceptsType" minOccurs="0"/>
 *         &lt;element name="SimpleConcepts" type="{}ServerResponseConceptExtractorResultConceptsType" minOccurs="0"/>
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
@XmlType(name = "ServerResponseConceptExtractorResultType", propOrder = {
    "complexConcepts",
    "simpleConcepts"
})
public class ServerResponseConceptExtractorResultType {

    @XmlElement(name = "ComplexConcepts")
    protected ServerResponseConceptExtractorResultConceptsType complexConcepts;
    @XmlElement(name = "SimpleConcepts")
    protected ServerResponseConceptExtractorResultConceptsType simpleConcepts;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the complexConcepts property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public ServerResponseConceptExtractorResultConceptsType getComplexConcepts() {
        return complexConcepts;
    }

    /**
     * Sets the value of the complexConcepts property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public void setComplexConcepts(ServerResponseConceptExtractorResultConceptsType value) {
        this.complexConcepts = value;
    }

    /**
     * Gets the value of the simpleConcepts property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public ServerResponseConceptExtractorResultConceptsType getSimpleConcepts() {
        return simpleConcepts;
    }

    /**
     * Sets the value of the simpleConcepts property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseConceptExtractorResultConceptsType }
     *     
     */
    public void setSimpleConcepts(ServerResponseConceptExtractorResultConceptsType value) {
        this.simpleConcepts = value;
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
