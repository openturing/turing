/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

}
