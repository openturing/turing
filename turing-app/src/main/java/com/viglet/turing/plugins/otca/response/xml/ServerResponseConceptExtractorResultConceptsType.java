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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseConceptExtractorResultConceptsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseConceptExtractorResultConceptsType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element name="Concept" type="{}ServerResponseConceptExtractorResultConcept1Type"/>
 *           &lt;element name="ExtractedTerm" type="{}ServerResponseConceptExtractorResultConcept2Type"/>
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
@XmlType(name = "ServerResponseConceptExtractorResultConceptsType", propOrder = {
    "conceptOrExtractedTerm"
})
public class ServerResponseConceptExtractorResultConceptsType {

    @XmlElements({
        @XmlElement(name = "ExtractedTerm", type = ServerResponseConceptExtractorResultConcept2Type.class),
        @XmlElement(name = "Concept", type = ServerResponseConceptExtractorResultConcept1Type.class)
    })
    protected List<Object> conceptOrExtractedTerm;

    /**
     * Gets the value of the conceptOrExtractedTerm property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the conceptOrExtractedTerm property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getConceptOrExtractedTerm().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServerResponseConceptExtractorResultConcept2Type }
     * {@link ServerResponseConceptExtractorResultConcept1Type }
     * 
     * 
     */
    public List<Object> getConceptOrExtractedTerm() {
        if (conceptOrExtractedTerm == null) {
            conceptOrExtractedTerm = new ArrayList<Object>();
        }
        return this.conceptOrExtractedTerm;
    }

}
