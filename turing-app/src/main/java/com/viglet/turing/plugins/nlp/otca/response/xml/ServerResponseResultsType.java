/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.plugins.nlp.otca.response.xml;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServerResponseResultsType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServerResponseResultsType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element name="Ping" type="{}ServerResponsePingResultType"/>
 *           &lt;element name="GetSupportedEncodings" type="{}ServerResponseGetSupportedEncodingsResultType"/>
 *           &lt;element name="languagedetector" type="{}ServerResponseLanguageDetectorResultType"/>
 *           &lt;element name="nconceptextractor" type="{}ServerResponseConceptExtractorResultType"/>
 *           &lt;element name="ncategorizer" type="{}ServerResponseCategorizerResultType"/>
 *           &lt;element name="nfinder" type="{}ServerResponseEntityExtractorResultType"/>
 *           &lt;element name="nsentiment" type="{}ServerResponseSentimentResultType"/>
 *           &lt;element name="nlikethis" type="{}ServerResponseSimilarityResultType"/>
 *           &lt;element name="nsummarizer" type="{}ServerResponseSummarizerResultType"/>
 *         &lt;/choice>
 *         &lt;choice maxOccurs="unbounded" minOccurs="0">
 *           &lt;element name="LanguageDetector" type="{}ServerResponseLanguageDetectorResultType"/>
 *           &lt;element name="NConceptExtractor" type="{}ServerResponseConceptExtractorResultType"/>
 *           &lt;element name="NCategorizer" type="{}ServerResponseCategorizerResultType"/>
 *           &lt;element name="NFinder" type="{}ServerResponseEntityExtractorResultType"/>
 *           &lt;element name="NSentiment" type="{}ServerResponseSentimentResultType"/>
 *           &lt;element name="NLikeThis" type="{}ServerResponseSimilarityResultType"/>
 *           &lt;element name="NSummarizer" type="{}ServerResponseSummarizerResultType"/>
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
@XmlType(name = "ServerResponseResultsType", propOrder = {
    "pingOrGetSupportedEncodingsOrLanguagedetector",
    "languageDetectorOrNConceptExtractorOrNCategorizer"
})
public class ServerResponseResultsType {

    @XmlElements({
        @XmlElement(name = "Ping", type = ServerResponsePingResultType.class),
        @XmlElement(name = "nlikethis", type = ServerResponseSimilarityResultType.class),
        @XmlElement(name = "ncategorizer", type = ServerResponseCategorizerResultType.class),
        @XmlElement(name = "nconceptextractor", type = ServerResponseConceptExtractorResultType.class),
        @XmlElement(name = "nsentiment", type = ServerResponseSentimentResultType.class),
        @XmlElement(name = "nfinder", type = ServerResponseEntityExtractorResultType.class),
        @XmlElement(name = "languagedetector", type = ServerResponseLanguageDetectorResultType.class),
        @XmlElement(name = "GetSupportedEncodings", type = ServerResponseGetSupportedEncodingsResultType.class),
        @XmlElement(name = "nsummarizer", type = ServerResponseSummarizerResultType.class)
    })
    protected List<Object> pingOrGetSupportedEncodingsOrLanguagedetector;
    @XmlElements({
        @XmlElement(name = "NLikeThis", type = ServerResponseSimilarityResultType.class),
        @XmlElement(name = "NSentiment", type = ServerResponseSentimentResultType.class),
        @XmlElement(name = "NCategorizer", type = ServerResponseCategorizerResultType.class),
        @XmlElement(name = "LanguageDetector", type = ServerResponseLanguageDetectorResultType.class),
        @XmlElement(name = "NConceptExtractor", type = ServerResponseConceptExtractorResultType.class),
        @XmlElement(name = "NSummarizer", type = ServerResponseSummarizerResultType.class),
        @XmlElement(name = "NFinder", type = ServerResponseEntityExtractorResultType.class)
    })
    protected List<Object> languageDetectorOrNConceptExtractorOrNCategorizer;

    /**
     * Gets the value of the pingOrGetSupportedEncodingsOrLanguagedetector property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pingOrGetSupportedEncodingsOrLanguagedetector property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPingOrGetSupportedEncodingsOrLanguagedetector().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServerResponsePingResultType }
     * {@link ServerResponseSimilarityResultType }
     * {@link ServerResponseCategorizerResultType }
     * {@link ServerResponseConceptExtractorResultType }
     * {@link ServerResponseSentimentResultType }
     * {@link ServerResponseEntityExtractorResultType }
     * {@link ServerResponseLanguageDetectorResultType }
     * {@link ServerResponseGetSupportedEncodingsResultType }
     * {@link ServerResponseSummarizerResultType }
     * 
     * 
     */
    public List<Object> getPingOrGetSupportedEncodingsOrLanguagedetector() {
        if (pingOrGetSupportedEncodingsOrLanguagedetector == null) {
            pingOrGetSupportedEncodingsOrLanguagedetector = new ArrayList<>();
        }
        return this.pingOrGetSupportedEncodingsOrLanguagedetector;
    }

    /**
     * Gets the value of the languageDetectorOrNConceptExtractorOrNCategorizer property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the languageDetectorOrNConceptExtractorOrNCategorizer property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getLanguageDetectorOrNConceptExtractorOrNCategorizer().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServerResponseSimilarityResultType }
     * {@link ServerResponseSentimentResultType }
     * {@link ServerResponseCategorizerResultType }
     * {@link ServerResponseLanguageDetectorResultType }
     * {@link ServerResponseConceptExtractorResultType }
     * {@link ServerResponseSummarizerResultType }
     * {@link ServerResponseEntityExtractorResultType }
     * 
     * 
     */
    public List<Object> getLanguageDetectorOrNConceptExtractorOrNCategorizer() {
        if (languageDetectorOrNConceptExtractorOrNCategorizer == null) {
            languageDetectorOrNConceptExtractorOrNCategorizer = new ArrayList<>();
        }
        return this.languageDetectorOrNConceptExtractorOrNCategorizer;
    }

}
