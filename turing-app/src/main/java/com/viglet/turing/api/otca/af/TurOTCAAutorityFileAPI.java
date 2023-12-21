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
package com.viglet.turing.api.otca.af;

import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLPRelationType;
import com.viglet.turing.nlp.TurNLPTermAccent;
import com.viglet.turing.nlp.TurNLPTermCase;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.term.*;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.term.*;
import com.viglet.turing.plugins.nlp.otca.af.xml.*;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType.Attributes;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType.Relations;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType.Variations;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFType.Terms;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
@Slf4j
@RestController
@RequestMapping("/api/otca/af")
@Tag(name = "OTCA", description = "OTCA API")
public class TurOTCAAutorityFileAPI {
	private final TurNLPEntityRepository turNLPEntityRepository;
	private final TurTermRepository turTermRepository;
	private final TurTermAttributeRepository turTermAttributeRepository;
	private final TurTermRelationFromRepository turTermRelationFromRepository;
	private final TurTermRelationToRepository turTermRelationToRepository;
	private final TurTermVariationRepository turTermVariationRepository;
	private final TurTermVariationLanguageRepository turTermVariationLanguageRepository;
	private static final String EMPTY_TERM_NAME = "<EMPTY>";

	@Inject
	public TurOTCAAutorityFileAPI(TurNLPEntityRepository turNLPEntityRepository,
								  TurTermRepository turTermRepository,
								  TurTermAttributeRepository turTermAttributeRepository,
								  TurTermRelationFromRepository turTermRelationFromRepository,
								  TurTermRelationToRepository turTermRelationToRepository,
								  TurTermVariationRepository turTermVariationRepository,
								  TurTermVariationLanguageRepository turTermVariationLanguageRepository) {
		this.turNLPEntityRepository = turNLPEntityRepository;
		this.turTermRepository = turTermRepository;
		this.turTermAttributeRepository = turTermAttributeRepository;
		this.turTermRelationFromRepository = turTermRelationFromRepository;
		this.turTermRelationToRepository = turTermRelationToRepository;
		this.turTermVariationRepository = turTermVariationRepository;
		this.turTermVariationLanguageRepository = turTermVariationLanguageRepository;
	}

	public String normalizeEntity(String s) {
		s = TurCommonsUtils.stripAccents(s).toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "")
				.replace(" ", "_");
		return s;
	}

	public TurNLPEntity setEntity(String name, String description) {

		TurNLPEntity turNLPEntity = this.turNLPEntityRepository.findByName(name);
		if (turNLPEntity == null) {
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName(name);
            turNLPEntity.setDescription(Objects.requireNonNullElse(description, ""));
			turNLPEntity.setInternalName(normalizeEntity(name));
			turNLPEntity.setLocal(1);
			turNLPEntity.setCollectionName(normalizeEntity(name));
			this.turNLPEntityRepository.save(turNLPEntity);
		}
		return turNLPEntity;
	}

	public void setTermAttribute(TurTerm turTerm, Attributes attributes) {

		if (attributes != null) {
			for (AFAttributeType afAttributeType : attributes.getAttribute()) {
				for (String value : afAttributeType.getValues().getValue()) {
					TurTermAttribute turTermAttribute = new TurTermAttribute();
					turTermAttribute.setValue(value);
					turTermAttribute.setTurTerm(turTerm);
					this.turTermAttributeRepository.save(turTermAttribute);
				}
			}
		}

	}

	public void setTermRelation(TurTerm turTerm, Relations relations) {

		if (relations != null) {
			for (AFTermRelationType afTermRelationType : relations.getRelation()) {
				TurTermRelationFrom turTermRelationFrom = new TurTermRelationFrom();
				if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.BT)) {
					turTermRelationFrom.setRelationType(TurNLPRelationType.BT.id());
				} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.NT)) {
					turTermRelationFrom.setRelationType(TurNLPRelationType.NT.id());
				} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.RT)) {
					turTermRelationFrom.setRelationType(TurNLPRelationType.RT.id());
				} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.U)) {
					turTermRelationFrom.setRelationType(TurNLPRelationType.U.id());
				} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.UF)) {
					turTermRelationFrom.setRelationType(TurNLPRelationType.UF.id());
				}
				turTermRelationFrom.setTurTerm(turTerm);
				this.turTermRelationFromRepository.save(turTermRelationFrom);

				TurTermRelationTo turTermRelationTo = new TurTermRelationTo();
				turTermRelationTo.setTurTermRelationFrom(turTermRelationFrom);
				TurTerm turTermTo = this.turTermRepository.findOneByIdCustom(afTermRelationType.getId());

				if (turTermTo == null) {
					turTermTo = new TurTerm();
					turTermTo.setIdCustom(afTermRelationType.getId());
					turTermTo.setName(EMPTY_TERM_NAME);
					turTermTo.setTurNLPEntity(turTermRelationFrom.getTurTerm().getTurNLPEntity());
					this.turTermRepository.save(turTermTo);

				}
				turTermRelationTo.setTurTerm(turTermTo);
				this.turTermRelationToRepository.save(turTermRelationTo);
			}
		}

	}

	public void setTermVariation(TurTerm turTerm, Variations variations) {

		for (AFTermVariationType afTermVariationType : variations.getVariation()) {
			TurTermVariation turTermVariation = new TurTermVariation();
			turTermVariation.setName(TurCommonsUtils.removeDuplicateWhiteSpaces(afTermVariationType.getName()));
			turTermVariation.setNameLower(TurCommonsUtils.stripAccents(turTermVariation.getName()).toLowerCase());
			
			setRuleAccent(afTermVariationType, turTermVariation);

			setRuleCase(afTermVariationType, turTermVariation);

			saveTermVariation(turTerm, afTermVariationType, turTermVariation);

			saveTermVariationLanguages(turTerm, afTermVariationType, turTermVariation);

		}
	}

	private void setRuleAccent(AFTermVariationType afTermVariationType, TurTermVariation turTermVariation) {
		if (afTermVariationType.getAccent().equals(AFTermVariationAccentEnum.AI))
			turTermVariation.setRuleAccent(TurNLPTermAccent.AI.id());
		else
			turTermVariation.setRuleAccent(TurNLPTermAccent.AS.id());
	}

	private void setRuleCase(AFTermVariationType afTermVariationType, TurTermVariation turTermVariation) {
		if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.CI))
			turTermVariation.setRuleCase(TurNLPTermCase.CI.id());
		else if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.CS))
			turTermVariation.setRuleCase(TurNLPTermCase.CS.id());
		else if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.UCS))
			turTermVariation.setRuleCase(TurNLPTermCase.UCS.id());
	}

	private void saveTermVariation(TurTerm turTerm, AFTermVariationType afTermVariationType,
			TurTermVariation turTermVariation) {
		setVariationRulePrefix(afTermVariationType, turTermVariation);
		setVariationRuleSuffix(afTermVariationType, turTermVariation);
		turTermVariation.setWeight(afTermVariationType.getWeight());
		turTermVariation.setTurTerm(turTerm);
		this.turTermVariationRepository.save(turTermVariation);
	}

	private void saveTermVariationLanguages(TurTerm turTerm, AFTermVariationType afTermVariationType,
			TurTermVariation turTermVariation) {
		for (String language : afTermVariationType.getLanguages().getLanguage()) {
			TurTermVariationLanguage turTermVariationLanguage = new TurTermVariationLanguage();
			turTermVariationLanguage.setLanguage(language);
			turTermVariationLanguage.setTurTerm(turTerm);
			turTermVariationLanguage.setTurTermVariation(turTermVariation);
			this.turTermVariationLanguageRepository.save(turTermVariationLanguage);
		}
	}

	private void setVariationRuleSuffix(AFTermVariationType afTermVariationType, TurTermVariation turTermVariation) {
		if (afTermVariationType.getSuffix() != null) {
			turTermVariation.setRuleSuffix(afTermVariationType.getSuffix().getValue());
			turTermVariation.setRuleSuffixRequired(
					Boolean.TRUE.equals(afTermVariationType.getSuffix().isRequired()) ? 1 : 0);
		}
	}

	private void setVariationRulePrefix(AFTermVariationType afTermVariationType, TurTermVariation turTermVariation) {
		if (afTermVariationType.getPrefix() != null) {
			turTermVariation.setRulePrefix(afTermVariationType.getPrefix().getValue());
			turTermVariation.setRulePrefixRequired(
					Boolean.TRUE.equals(afTermVariationType.getPrefix().isRequired()) ? 1 : 0);
		}
	}

	public void setTerms(TurNLPEntity turNLPEntity, Terms terms) {
		boolean overwrite;

		for (AFTermType afTermType : terms.getTerm()) {
			String termId = afTermType.getId();
			TurTerm turTerm = this.turTermRepository.findOneByIdCustom(termId);

			if (turTerm != null) {
				// Term that was created during relation but the parent
				// wasn't created yet
				overwrite = turTerm.getName().equals(EMPTY_TERM_NAME);
			} else {
				turTerm = new TurTerm();
				overwrite = true;
			}

			if (overwrite) {
				turTerm.setIdCustom(termId);
				turTerm.setName(afTermType.getName());
				turTerm.setTurNLPEntity(turNLPEntity);
				this.turTermRepository.save(turTerm);

				this.setTermVariation(turTerm, afTermType.getVariations());
				this.setTermRelation(turTerm, afTermType.getRelations());
				this.setTermAttribute(turTerm, afTermType.getAttributes());
			}
		}

	}

	@PostMapping("/import")
	@Transactional
	public RedirectView turOTCAAutorityFileImport(@RequestParam("file") MultipartFile multipartFile){

		try {
			JAXBContext jaxbContext = JAXBContext
					.newInstance(com.viglet.turing.plugins.nlp.otca.af.xml.ObjectFactory.class);
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			spf.setFeature("http://xml.org/sax/features/validation", false);
			spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			XMLReader xmlReader = spf.newSAXParser().getXMLReader();
			SAXSource source = new SAXSource(xmlReader, new InputSource(multipartFile.getInputStream()));

			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

			AFType documentType = unmarshaller.unmarshal(source, AFType.class).getValue();

			TurNLPEntity turNLPEntity = this.setEntity(documentType.getName(), documentType.getDescription());
			this.setTerms(turNLPEntity, documentType.getTerms());

		} catch (Exception ex) {
			log.error(ex.getMessage(), ex);
		}
		String redirect = "/turing/#entity/import";

		RedirectView redirectView = new RedirectView(
				new String(redirect.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
		redirectView.setHttp10Compatible(false);
		return redirectView;
	}
}
