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
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseSentimentResultSentenceType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseSentimentResultSentenceType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="Text" type="{}ServerResponseSentimentResultSentenceTextType" minOccurs="0"/>
 *         &lt;element name="Subjectivity" type="{}ServerResponseSentimentResultSentenceSubjectivityType" minOccurs="0"/>
 *         &lt;element name="PositiveTone" type="{}ServerResponseSentimentResultSentenceToneType" minOccurs="0"/>
 *         &lt;element name="NegativeTone" type="{}ServerResponseSentimentResultSentenceToneType" minOccurs="0"/>
 *         &lt;element name="Tone" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServerResponseSentimentResultSentenceType", propOrder = {
    "text",
    "subjectivity",
    "positiveTone",
    "negativeTone",
    "tone"
})
public class ServerResponseSentimentResultSentenceType {

    @XmlElement(name = "Text")
    protected ServerResponseSentimentResultSentenceTextType text;
    @XmlElement(name = "Subjectivity")
    protected ServerResponseSentimentResultSentenceSubjectivityType subjectivity;
    @XmlElement(name = "PositiveTone")
    protected ServerResponseSentimentResultSentenceToneType positiveTone;
    @XmlElement(name = "NegativeTone")
    protected ServerResponseSentimentResultSentenceToneType negativeTone;
    @XmlElement(name = "Tone")
    protected String tone;

    /**
     * Gets the value of the text property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultSentenceTextType }
     *     
     */
    public ServerResponseSentimentResultSentenceTextType getText() {
        return text;
    }

    /**
     * Sets the value of the text property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultSentenceTextType }
     *     
     */
    public void setText(ServerResponseSentimentResultSentenceTextType value) {
        this.text = value;
    }

    /**
     * Gets the value of the subjectivity property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultSentenceSubjectivityType }
     *     
     */
    public ServerResponseSentimentResultSentenceSubjectivityType getSubjectivity() {
        return subjectivity;
    }

    /**
     * Sets the value of the subjectivity property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultSentenceSubjectivityType }
     *     
     */
    public void setSubjectivity(ServerResponseSentimentResultSentenceSubjectivityType value) {
        this.subjectivity = value;
    }

    /**
     * Gets the value of the positiveTone property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultSentenceToneType }
     *     
     */
    public ServerResponseSentimentResultSentenceToneType getPositiveTone() {
        return positiveTone;
    }

    /**
     * Sets the value of the positiveTone property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultSentenceToneType }
     *     
     */
    public void setPositiveTone(ServerResponseSentimentResultSentenceToneType value) {
        this.positiveTone = value;
    }

    /**
     * Gets the value of the negativeTone property.
     * 
     * @return
     *     possible object is
     *     {@link ServerResponseSentimentResultSentenceToneType }
     *     
     */
    public ServerResponseSentimentResultSentenceToneType getNegativeTone() {
        return negativeTone;
    }

    /**
     * Sets the value of the negativeTone property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServerResponseSentimentResultSentenceToneType }
     *     
     */
    public void setNegativeTone(ServerResponseSentimentResultSentenceToneType value) {
        this.negativeTone = value;
    }

    /**
     * Gets the value of the tone property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getTone() {
        return tone;
    }

    /**
     * Sets the value of the tone property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setTone(String value) {
        this.tone = value;
    }

}
