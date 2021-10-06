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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseGetSupportedEncodingsResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseGetSupportedEncodingsResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Encodings" type="{}ServerResponseGetSupportedEncodingsResultEncodingsType" minOccurs="0"/>
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
@XmlType(name = "ServerResponseGetSupportedEncodingsResultType", propOrder = {
    "encodings"
})
public class ServerResponseGetSupportedEncodingsResultType {

    @XmlElement(name = "Encodings")
    protected ServerResponseGetSupportedEncodingsResultEncodingsType encodings;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the encodings property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseGetSupportedEncodingsResultEncodingsType }
     *     
     */
    public ServerResponseGetSupportedEncodingsResultEncodingsType getEncodings() {
        return encodings;
    }

    /**
     * Sets the value of the encodings property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseGetSupportedEncodingsResultEncodingsType }
     *     
     */
    public void setEncodings(ServerResponseGetSupportedEncodingsResultEncodingsType value) {
        this.encodings = value;
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
