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
package com.viglet.turing.nlp;

public class TurNLPEntityConstant {
	private TurNLPEntityConstant() {
		throw new IllegalStateException("NLP Entity Constant class");
	}

	public static final String PERSON_INTERNAL = "PN";
	public static final String PERSON_CORENLP = "PERSON";
	public static final String PERSON_OTCA = "PN";
	public static final String PERSON_POLYGLOT = "PERSON";
	public static final String PERSON_SPACY = "PERSON";

	public static final String LOCATION_INTERNAL = "GL";
	public static final String LOCATION_CORENLP = "LOCATION";
	public static final String LOCATION_OTCA = "GL";
	public static final String LOCATION_POLYGLOT = "LOC";
	public static final String LOCATION_SPACY = "LOC";

	public static final String ORGANIZATION_INTERNAL = "ON";
	public static final String ORGANIZATION_CORENLP = "ORGANIZATION";
	public static final String ORGANIZATION_OTCA = "ON";
	public static final String ORGANIZATION_POLYGLOT = "ORG";
	public static final String ORGANIZATION_SPACY = "ORG";

	public static final String DURATION_INTERNAL = "DURATION";
	public static final String DURATION_CORENLP = "DURATION";
	public static final String DURATION_SPACY = "DURATION";

	public static final String DATE_INTERNAL = "DATE";
	public static final String DATE_CORENLP = "DATE";
	public static final String DATE_SPACY = "DATE";

	public static final String MISC_INTERNAL = "MISC";
	public static final String MISC_CORENLP = "MISC";
	public static final String MISC_SPACY = "MISC";

	public static final String ORDINAL_INTERNAL = "ORDINAL";
	public static final String ORDINAL_CORENLP = "ORDINAL";
	public static final String ORDINAL_SPACY = "ORDINAL";

	public static final String TIME_INTERNAL = "TIME";
	public static final String TIME_CORENLP = "TIME";
	public static final String TIME_SPACY = "TIME";

	public static final String MONEY_INTERNAL = "MONEY";
	public static final String MONEY_SPACY = "MONEY";

	public static final String PERCENTAGE_INTERNAL = "PERCENTAGE";
	public static final String PERCENTAGE_SPACY = "PERCENT";

	public static final String FIRST_NAME_INTERNAL = "FIRST_NAME";
	public static final String FIRST_NAME_POLYGLOT = "FIRST_NAME";

	public static final String LAST_NAME_INTERNAL = "LAST_NAME";
	public static final String LAST_NAME_POLYGLOT = "LAST_NAME";

	public static final String EMAIL_INTERNAL = "EMAIL";
	public static final String EMAIL_POLYGLOT = "EMAIL";

	public static final String NIE_INTERNAL = "NIE";
	public static final String NIE_POLYGLOT = "NIE";

	public static final String CIF_INTERNAL = "CIF";
	public static final String CIF_POLYGLOT = "CIF";

	public static final String DNI_INTERNAL = "DNI";
	public static final String DNI_POLYGLOT = "DNI";

	public static final String PASSPORT_INTERNAL = "PASSPORT";
	public static final String PASSPORT_POLYGLOT = "PASSPORT";

	public static final String NORP_INTERNAL = "NORP";
	public static final String NORP_SPACY = "NORP";

	public static final String FAC_INTERNAL = "FAC";
	public static final String FAC_SPACY = "FAC";

	public static final String GPE_INTERNAL = "GPE";
	public static final String GPE_SPACY = "GPE";

	public static final String PRODUCT_INTERNAL = "PRODUCT";
	public static final String PRODUCT_SPACY = "PRODUCT";

	public static final String EVENT_INTERNAL = "EVENT";
	public static final String EVENT_SPACY = "EVENT";

	public static final String WORK_OF_ART_INTERNAL = "WORK_OF_ART";
	public static final String WORK_OF_ART_SPACY = "WORK_OF_ART";

	public static final String LAW_INTERNAL = "LAW";
	public static final String LAW_SPACY = "LAW";

	public static final String LANGUAGE_INTERNAL = "LANGUAGE";
	public static final String LANGUAGE_SPACY = "LANGUAGE";

	public static final String QUANTITY_INTERNAL = "QUANTITY";
	public static final String QUANTITY_SPACY = "QUANTITY";

	public static final String CARDINAL_INTERNAL = "CARDINAL";
	public static final String CARDINAL_SPACY = "CARDINAL";
	
	public static final String PHONE_NUMBER = "PHONE_NUMBER";
	
	public static final String ADDRESS = "ADDRESS";
	
	public static final String CONSUMER_GOOD = "CONSUMER_GOOD";
}
