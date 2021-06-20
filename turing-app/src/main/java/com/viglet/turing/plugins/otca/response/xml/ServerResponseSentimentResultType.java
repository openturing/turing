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
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseSentimentResultType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseSentimentResultType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="SentenceLevel" minOccurs="0">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;sequence>
 *                   &lt;element name="Sentence" type="{}ServerResponseSentimentResultSentenceType" maxOccurs="unbounded" minOccurs="0"/>
 *                 &lt;/sequence>
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *         &lt;element name="DocumentLevel" type="{}ServerResponseSentimentResultDocumentType" minOccurs="0"/>
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
@XmlType(name = "ServerResponseSentimentResultType", propOrder = {
    "sentenceLevel",
    "documentLevel"
})
public class ServerResponseSentimentResultType {

    @XmlElement(name = "SentenceLevel")
    protected ServerResponseSentimentResultType.SentenceLevel sentenceLevel;
    @XmlElement(name = "DocumentLevel")
    protected ServerResponseSentimentResultDocumentType documentLevel;
    @XmlAttribute(name = "Name")
    protected String name;

    /**
     * Gets the value of the sentenceLevel property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultType.SentenceLevel }
     *     
     */
    public ServerResponseSentimentResultType.SentenceLevel getSentenceLevel() {
        return sentenceLevel;
    }

    /**
     * Sets the value of the sentenceLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultType.SentenceLevel }
     *     
     */
    public void setSentenceLevel(ServerResponseSentimentResultType.SentenceLevel value) {
        this.sentenceLevel = value;
    }

    /**
     * Gets the value of the documentLevel property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultDocumentType }
     *     
     */
    public ServerResponseSentimentResultDocumentType getDocumentLevel() {
        return documentLevel;
    }

    /**
     * Sets the value of the documentLevel property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultDocumentType }
     *     
     */
    public void setDocumentLevel(ServerResponseSentimentResultDocumentType value) {
        this.documentLevel = value;
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
     *         &lt;element name="Sentence" type="{}ServerResponseSentimentResultSentenceType" maxOccurs="unbounded" minOccurs="0"/>
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
        "sentence"
    })
    public static class SentenceLevel {

        @XmlElement(name = "Sentence")
        protected List<ServerResponseSentimentResultSentenceType> sentence;

        /**
         * Gets the value of the sentence property.
         * 
         * <p>
         * This accessor method returns a reference to the live list,
         * not a snapshot. Therefore any modification you make to the
         * returned list will be present inside the JAXB object.
         * This is why there is not a <CODE>set</CODE> method for the sentence property.
         * 
         * <p>
         * For example, to add a new item, do as follows:
         * <pre>
         *    getSentence().add(newItem);
         * </pre>
         * 
         * 
         * <p>
         * Objects of the following type(s) are allowed in the list
         * {@link ServerResponseSentimentResultSentenceType }
         * 
         * 
         */
        public List<ServerResponseSentimentResultSentenceType> getSentence() {
            if (sentence == null) {
                sentence = new ArrayList<ServerResponseSentimentResultSentenceType>();
            }
            return this.sentence;
        }

    }

}
