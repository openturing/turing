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

package com.viglet.turing.api.otca.af;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLPRelationType;
import com.viglet.turing.nlp.TurNLPTermAccent;
import com.viglet.turing.nlp.TurNLPTermCase;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.model.nlp.term.TurTermAttribute;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariationLanguage;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.term.TurTermAttributeRepository;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRelationFromRepository;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRelationToRepository;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRepository;
import com.viglet.turing.persistence.repository.nlp.term.TurTermVariationLanguageRepository;
import com.viglet.turing.persistence.repository.nlp.term.TurTermVariationRepository;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFAttributeType;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermRelationType;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermRelationTypeEnum;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType.Attributes;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType.Relations;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermType.Variations;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermVariationAccentEnum;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermVariationCaseEnum;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFTermVariationType;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFType;
import com.viglet.turing.plugins.nlp.otca.af.xml.AFType.Terms;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/otca/af")
@Tag(name = "OTCA", description = "OTCA API")
public class TurOTCAAutorityFileAPI {
	private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	private TurTermRepository turTermRepository;
	@Autowired
	private TurTermAttributeRepository turTermAttributeRepository;
	@Autowired
	private TurTermRelationFromRepository turTermRelationFromRepository;
	@Autowired
	private TurTermRelationToRepository turTermRelationToRepository;
	@Autowired
	private TurTermVariationRepository turTermVariationRepository;
	@Autowired
	private TurTermVariationLanguageRepository turTermVariationLanguageRepository;

	private static final String EMPTY_TERM_NAME = "<EMPTY>";

	public String normalizeEntity(String s) {
		s = TurCommonsUtils.stripAccents(s).toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").replace(" ", "_");
		return s;
	}

	public TurNLPEntity setEntity(String name, String description) {

		TurNLPEntity turNLPEntity = this.turNLPEntityRepository.findByName(name);
		if (turNLPEntity == null) {
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName(name);
			if (description != null) {
				turNLPEntity.setDescription(description);
			} else {
				turNLPEntity.setDescription("");
			}
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
		boolean overwrite = false;

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
	public RedirectView turOTCAAutorityFileImport(@RequestParam("file") MultipartFile multipartFile,
			HttpServletRequest request) {

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

		} catch (JAXBException ejaxb) {
			logger.error(ejaxb.getMessage(), ejaxb);
		} catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
		}
		String redirect = "/turing/#entity/import";

		RedirectView redirectView = new RedirectView(
				new String(redirect.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
		redirectView.setHttp10Compatible(false);
		return redirectView;
	}
}
