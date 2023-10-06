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
package com.viglet.turing.plugins.nlp.otca.af.xml;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;

/**
 * This object contains factory methods for each Java content interface and Java
 * element interface generated in the com.viglet.turing.plugins.otca.af.xml
 * package.
 * <p>
 * An ObjectFactory allows you to programatically construct new instances of the
 * Java representation for XML content. The Java representation of XML content
 * can consist of schema derived interfaces and classes representing the binding
 * of schema type definitions, element declarations and model groups. Factory
 * methods for each of these are provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {
	private static final String AUTORITY_FILE_QNAME = "http://dev.nstein.com/tme-authority-file/4.10";
	private static final QName _Languages_QNAME = new QName("http://dev.nstein.com/common/1.3", "languages");
	private static final QName _AuthorityFiles_QNAME = new QName(AUTORITY_FILE_QNAME, "authorityFiles");
	private static final QName _Config_QNAME = new QName(AUTORITY_FILE_QNAME, "config");
	private static final QName _Term_QNAME = new QName(AUTORITY_FILE_QNAME, "term");
	private static final QName _AuthorityFile_QNAME = new QName(AUTORITY_FILE_QNAME, "authorityFile");
	private static final QName _Terms_QNAME = new QName(AUTORITY_FILE_QNAME, "terms");
	private static final QName _Names_QNAME = new QName(AUTORITY_FILE_QNAME, "names");

	/**
	 * Create a new ObjectFactory that can be used to create new instances of schema
	 * derived classes for package: com.viglet.turing.plugins.otca.af.xml
	 * 
	 */
	public ObjectFactory() {
		// EMPTY
	}

	/**
	 * Create an instance of {@link AFAttributeType }
	 * 
	 */
	public AFAttributeType createAFAttributeType() {
		return new AFAttributeType();
	}

	/**
	 * Create an instance of {@link AFConfigType.SearchTypes }
	 * 
	 */
	public AFConfigType.SearchTypes createAFConfigTypeSearchTypes() {
		return new AFConfigType.SearchTypes();
	}

	/**
	 * Create an instance of {@link AFConfigRuleActionDeleteType }
	 * 
	 */
	public AFConfigRuleActionDeleteType createAFConfigRuleActionDeleteType() {
		return new AFConfigRuleActionDeleteType();
	}

	/**
	 * Create an instance of {@link AFAttributeType.Values }
	 * 
	 */
	public AFAttributeType.Values createAFAttributeTypeValues() {
		return new AFAttributeType.Values();
	}

	/**
	 * Create an instance of {@link AFConfigRuleType }
	 * 
	 */
	public AFConfigRuleType createAFConfigRuleType() {
		return new AFConfigRuleType();
	}

	/**
	 * Create an instance of {@link LanguagesType }
	 * 
	 */
	public LanguagesType createLanguagesType() {
		return new LanguagesType();
	}

	/**
	 * Create an instance of {@link AFTermType.Attributes }
	 * 
	 */
	public AFTermType.Attributes createAFTermTypeAttributes() {
		return new AFTermType.Attributes();
	}

	/**
	 * Create an instance of {@link LanguageType }
	 * 
	 */
	public LanguageType createLanguageType() {
		return new LanguageType();
	}

	/**
	 * Create an instance of {@link AFType.Terms }
	 * 
	 */
	public AFType.Terms createAFTypeTerms() {
		return new AFType.Terms();
	}

	/**
	 * Create an instance of {@link AFConfigRuleType.Units }
	 * 
	 */
	public AFConfigRuleType.Units createAFConfigRuleTypeUnits() {
		return new AFConfigRuleType.Units();
	}

	/**
	 * Create an instance of {@link AFConfigType.Parameters }
	 * 
	 */
	public AFConfigType.Parameters createAFConfigTypeParameters() {
		return new AFConfigType.Parameters();
	}

	/**
	 * Create an instance of {@link AFType.Attributes }
	 * 
	 */
	public AFType.Attributes createAFTypeAttributes() {
		return new AFType.Attributes();
	}

	/**
	 * Create an instance of {@link AFTermVariationAffixType }
	 * 
	 */
	public AFTermVariationAffixType createAFTermVariationAffixType() {
		return new AFTermVariationAffixType();
	}

	/**
	 * Create an instance of {@link AFTermType }
	 * 
	 */
	public AFTermType createAFTermType() {
		return new AFTermType();
	}

	/**
	 * Create an instance of {@link AFType }
	 * 
	 */
	public AFType createAFType() {
		return new AFType();
	}

	/**
	 * Create an instance of {@link SearchTypeType }
	 * 
	 */
	public SearchTypeType createSearchTypeType() {
		return new SearchTypeType();
	}

	/**
	 * Create an instance of {@link AFTermVariationType.Languages }
	 * 
	 */
	public AFTermVariationType.Languages createAFTermVariationTypeLanguages() {
		return new AFTermVariationType.Languages();
	}

	/**
	 * Create an instance of {@link AFAttributeDefType.Values }
	 * 
	 */
	public AFAttributeDefType.Values createAFAttributeDefTypeValues() {
		return new AFAttributeDefType.Values();
	}

	/**
	 * Create an instance of {@link AFConfigRuleActionMergeRangeType }
	 * 
	 */
	public AFConfigRuleActionMergeRangeType createAFConfigRuleActionMergeRangeType() {
		return new AFConfigRuleActionMergeRangeType();
	}

	/**
	 * Create an instance of {@link AFConfigType.DisambiguationRules }
	 * 
	 */
	public AFConfigType.DisambiguationRules createAFConfigTypeDisambiguationRules() {
		return new AFConfigType.DisambiguationRules();
	}

	/**
	 * Create an instance of {@link AFConfigType }
	 * 
	 */
	public AFConfigType createAFConfigType() {
		return new AFConfigType();
	}

	/**
	 * Create an instance of {@link AFNamesType }
	 * 
	 */
	public AFNamesType createAFNamesType() {
		return new AFNamesType();
	}

	/**
	 * Create an instance of {@link AFConfigRuleUnitType }
	 * 
	 */
	public AFConfigRuleUnitType createAFConfigRuleUnitType() {
		return new AFConfigRuleUnitType();
	}

	/**
	 * Create an instance of {@link AFTermsType }
	 * 
	 */
	public AFTermsType createAFTermsType() {
		return new AFTermsType();
	}

	/**
	 * Create an instance of {@link AFTermRelationType }
	 * 
	 */
	public AFTermRelationType createAFTermRelationType() {
		return new AFTermRelationType();
	}

	/**
	 * Create an instance of {@link AFTermType.Variations }
	 * 
	 */
	public AFTermType.Variations createAFTermTypeVariations() {
		return new AFTermType.Variations();
	}

	/**
	 * Create an instance of {@link AFTermVariationType }
	 * 
	 */
	public AFTermVariationType createAFTermVariationType() {
		return new AFTermVariationType();
	}

	/**
	 * Create an instance of {@link AFConfigRuleActionMergeType }
	 * 
	 */
	public AFConfigRuleActionMergeType createAFConfigRuleActionMergeType() {
		return new AFConfigRuleActionMergeType();
	}

	/**
	 * Create an instance of {@link AFConfigType.ContiguityRules }
	 * 
	 */
	public AFConfigType.ContiguityRules createAFConfigTypeContiguityRules() {
		return new AFConfigType.ContiguityRules();
	}

	/**
	 * Create an instance of {@link AFConfigParameterType }
	 * 
	 */
	public AFConfigParameterType createAFConfigParameterType() {
		return new AFConfigParameterType();
	}

	/**
	 * Create an instance of {@link AFConfigRuleActionMergeRangeType.Separators }
	 * 
	 */
	public AFConfigRuleActionMergeRangeType.Separators createAFConfigRuleActionMergeRangeTypeSeparators() {
		return new AFConfigRuleActionMergeRangeType.Separators();
	}

	/**
	 * Create an instance of {@link NameType }
	 * 
	 */
	public NameType createNameType() {
		return new NameType();
	}

	/**
	 * Create an instance of {@link AFAttributeDefType }
	 * 
	 */
	public AFAttributeDefType createAFAttributeDefType() {
		return new AFAttributeDefType();
	}

	/**
	 * Create an instance of {@link AFObjectsType }
	 * 
	 */
	public AFObjectsType createAFObjectsType() {
		return new AFObjectsType();
	}

	/**
	 * Create an instance of {@link AFTermType.Relations }
	 * 
	 */
	public AFTermType.Relations createAFTermTypeRelations() {
		return new AFTermType.Relations();
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link LanguagesType
	 * }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/common/1.3", name = "languages")
	public JAXBElement<LanguagesType> createLanguages(LanguagesType value) {
		return new JAXBElement<>(_Languages_QNAME, LanguagesType.class, null, value);
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link AFObjectsType
	 * }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/tme-authority-file/4.10", name = "authorityFiles")
	public JAXBElement<AFObjectsType> createAuthorityFiles(AFObjectsType value) {
		return new JAXBElement<>(_AuthorityFiles_QNAME, AFObjectsType.class, null, value);
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link AFConfigType
	 * }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/tme-authority-file/4.10", name = "config")
	public JAXBElement<AFConfigType> createConfig(AFConfigType value) {
		return new JAXBElement<>(_Config_QNAME, AFConfigType.class, null, value);
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link AFTermType
	 * }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/tme-authority-file/4.10", name = "term")
	public JAXBElement<AFTermType> createTerm(AFTermType value) {
		return new JAXBElement<>(_Term_QNAME, AFTermType.class, null, value);
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link AFType }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/tme-authority-file/4.10", name = "authorityFile")
	public JAXBElement<AFType> createAuthorityFile(AFType value) {
		return new JAXBElement<>(_AuthorityFile_QNAME, AFType.class, null, value);
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link AFTermsType
	 * }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/tme-authority-file/4.10", name = "terms")
	public JAXBElement<AFTermsType> createTerms(AFTermsType value) {
		return new JAXBElement<>(_Terms_QNAME, AFTermsType.class, null, value);
	}

	/**
	 * Create an instance of {@link JAXBElement }{@code <}{@link AFNamesType
	 * }{@code >}}
	 * 
	 */
	@XmlElementDecl(namespace = "http://dev.nstein.com/tme-authority-file/4.10", name = "names")
	public JAXBElement<AFNamesType> createNames(AFNamesType value) {
		return new JAXBElement<>(_Names_QNAME, AFNamesType.class, null, value);
	}

}
