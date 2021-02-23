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

package com.viglet.turing.api.otca.af;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

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
import com.viglet.turing.plugins.otca.af.xml.AFAttributeType;
import com.viglet.turing.plugins.otca.af.xml.AFTermRelationType;
import com.viglet.turing.plugins.otca.af.xml.AFTermRelationTypeEnum;
import com.viglet.turing.plugins.otca.af.xml.AFTermType;
import com.viglet.turing.plugins.otca.af.xml.AFTermType.Attributes;
import com.viglet.turing.plugins.otca.af.xml.AFTermType.Relations;
import com.viglet.turing.plugins.otca.af.xml.AFTermType.Variations;
import com.viglet.turing.plugins.otca.af.xml.AFTermVariationAccentEnum;
import com.viglet.turing.plugins.otca.af.xml.AFTermVariationCaseEnum;
import com.viglet.turing.plugins.otca.af.xml.AFTermVariationType;
import com.viglet.turing.plugins.otca.af.xml.AFType;
import com.viglet.turing.plugins.otca.af.xml.AFType.Terms;
import com.viglet.turing.util.TurUtils;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/otca/af")
@Api(tags = "OTCA", description = "OTCA API")
public class TurOTCAAutorityFileAPI {
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
	@Autowired
	ServletContext servletContext;
	@Autowired
	TurUtils turUtils;

	final String EMPTY_TERM_NAME = "<EMPTY>";

	public String normalizeEntity(String s) {
		s = turUtils.stripAccents(s).toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").replaceAll(" ", "_");
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
			turTermVariation.setName(turUtils.removeDuplicateWhiteSpaces(afTermVariationType.getName()));
			turTermVariation.setNameLower(turUtils.stripAccents(turTermVariation.getName()).toLowerCase());
			if (afTermVariationType.getAccent().equals(AFTermVariationAccentEnum.AI))
				turTermVariation.setRuleAccent(TurNLPTermAccent.AI.id());
			else
				turTermVariation.setRuleAccent(TurNLPTermAccent.AS.id());

			if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.CI))
				turTermVariation.setRuleCase(TurNLPTermCase.CI.id());
			else if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.CS))
				turTermVariation.setRuleCase(TurNLPTermCase.CS.id());
			else if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.UCS))
				turTermVariation.setRuleCase(TurNLPTermCase.UCS.id());

			if (afTermVariationType.getPrefix() != null) {
				turTermVariation.setRulePrefix(afTermVariationType.getPrefix().getValue());
				turTermVariation.setRulePrefixRequired(afTermVariationType.getPrefix().isRequired() ? 1 : 0);
			}
			if (afTermVariationType.getSuffix() != null) {
				turTermVariation.setRuleSuffix(afTermVariationType.getSuffix().getValue());
				turTermVariation.setRuleSuffixRequired(afTermVariationType.getSuffix().isRequired() ? 1 : 0);
			}
			turTermVariation.setWeight(afTermVariationType.getWeight());
			turTermVariation.setTurTerm(turTerm);
			this.turTermVariationRepository.save(turTermVariation);

			for (String language : afTermVariationType.getLanguages().getLanguage()) {
				TurTermVariationLanguage turTermVariationLanguage = new TurTermVariationLanguage();
				turTermVariationLanguage.setLanguage(language);
				turTermVariationLanguage.setTurTerm(turTerm);
				turTermVariationLanguage.setTurTermVariation(turTermVariation);
				this.turTermVariationLanguageRepository.save(turTermVariationLanguage);
			}

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
				overwrite = turTerm.getName().equals(EMPTY_TERM_NAME) ? true : false;
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

	@SuppressWarnings("unchecked")
	@PostMapping("/import")
	@Transactional
	public RedirectView turOTCAAutorityFileImport(@RequestParam("file") MultipartFile multipartFile,
			HttpServletRequest request) throws URISyntaxException, UnsupportedEncodingException {

		try {
			JAXBContext jaxbContext = JAXBContext
					.newInstance(com.viglet.turing.plugins.otca.af.xml.ObjectFactory.class);
			AFType documentType = ((JAXBElement<AFType>) jaxbContext.createUnmarshaller()
					.unmarshal(multipartFile.getInputStream())).getValue();

			TurNLPEntity turNLPEntity = this.setEntity(documentType.getName(), documentType.getDescription());
			this.setTerms(turNLPEntity, documentType.getTerms());

		} catch (JAXBException ejaxb) {
			ejaxb.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		String redirect = "/turing/#entity/import";

		RedirectView redirectView = new RedirectView(new String(redirect.getBytes("UTF-8"), "ISO-8859-1"));
		redirectView.setHttp10Compatible(false);
		return redirectView;
	}
}
