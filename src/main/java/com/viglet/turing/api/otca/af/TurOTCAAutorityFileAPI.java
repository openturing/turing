package com.viglet.turing.api.otca.af;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import com.viglet.turing.nlp.TurNLPRelationType;
import com.viglet.turing.nlp.TurNLPTermAccent;
import com.viglet.turing.nlp.TurNLPTermCase;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.model.nlp.term.TurTermAttribute;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariationLanguage;
import com.viglet.turing.persistence.service.nlp.TurNLPEntityService;
import com.viglet.turing.persistence.service.nlp.term.TurTermAttributeService;
import com.viglet.turing.persistence.service.nlp.term.TurTermRelationFromService;
import com.viglet.turing.persistence.service.nlp.term.TurTermRelationToService;
import com.viglet.turing.persistence.service.nlp.term.TurTermService;
import com.viglet.turing.persistence.service.nlp.term.TurTermVariationLanguageService;
import com.viglet.turing.persistence.service.nlp.term.TurTermVariationService;
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
import com.viglet.util.TurUtils;

@Path("/otca/af")
public class TurOTCAAutorityFileAPI {
	TurNLPEntityService turNLPEntityService = new TurNLPEntityService();
	TurTermService turTermService = new TurTermService();
	TurTermAttributeService turTermAttributeService = new TurTermAttributeService();
	TurTermRelationFromService turTermRelationFromService = new TurTermRelationFromService();
	TurTermRelationToService turTermRelationToService = new TurTermRelationToService();
	TurTermVariationService turTermVariationService = new TurTermVariationService();
	TurTermVariationLanguageService turTermVariationLanguageService = new TurTermVariationLanguageService();
	final String EMPTY_TERM_NAME = "<EMPTY>";

	public static String normalizeEntity(String s) {
		s = TurUtils.stripAccents(s).toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").replaceAll(" ", "_");
		return s;
	}

	public TurNLPEntity setEntity(String name, String description) {

		TurNLPEntity turNLPEntity = turNLPEntityService.findByName(name);
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
			turNLPEntityService.save(turNLPEntity);
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
					turTermAttributeService.save(turTermAttribute);
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
				turTermRelationFromService.save(turTermRelationFrom);

				TurTermRelationTo turTermRelationTo = new TurTermRelationTo();
				turTermRelationTo.setTurTermRelationFrom(turTermRelationFrom);
				TurTerm turTermTo = turTermService.findByIdCustom(afTermRelationType.getId());

				if (turTermTo == null) {
					turTermTo = new TurTerm();
					turTermTo.setIdCustom(afTermRelationType.getId());
					turTermTo.setName(EMPTY_TERM_NAME);
					turTermTo.setTurNLPEntity(turTermRelationFrom.getTurTerm().getTurNLPEntity());
					turTermService.save(turTermTo);

				}
				turTermRelationTo.setTurTerm(turTermTo);
				turTermRelationToService.save(turTermRelationTo);
			}
		}

	}

	public void setTermVariation(TurTerm turTerm, Variations variations) {

		for (AFTermVariationType afTermVariationType : variations.getVariation()) {
			TurTermVariation turTermVariation = new TurTermVariation();
			turTermVariation.setName(TurUtils.removeDuplicateWhiteSpaces(afTermVariationType.getName()));
			turTermVariation.setNameLower(TurUtils.stripAccents(turTermVariation.getName()).toLowerCase());
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
			turTermVariationService.save(turTermVariation);

			for (String language : afTermVariationType.getLanguages().getLanguage()) {
				TurTermVariationLanguage turTermVariationLanguage = new TurTermVariationLanguage();
				turTermVariationLanguage.setLanguage(language);
				turTermVariationLanguage.setTurTerm(turTerm);
				turTermVariationLanguage.setTurTermVariation(turTermVariation);
				turTermVariationLanguageService.save(turTermVariationLanguage);
			}

		}
	}

	public void setTerms(TurNLPEntity turNLPEntity, Terms terms) {
		boolean overwrite = false;

		for (AFTermType afTermType : terms.getTerm()) {
			String termId = afTermType.getId();
			TurTerm turTerm = turTermService.findByIdCustom(termId);

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
				turTermService.save(turTerm);

				this.setTermVariation(turTerm, afTermType.getVariations());
				this.setTermRelation(turTerm, afTermType.getRelations());
				this.setTermAttribute(turTerm, afTermType.getAttributes());
			}
		}

	}

	@SuppressWarnings("unchecked")
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces("application/json")
	@Path("import")
	public Response fileUpload(@DefaultValue("true") @FormDataParam("enabled") boolean enabled,
			@FormDataParam("file") InputStream inputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail, @Context UriInfo uriInfo)
			throws URISyntaxException {

		System.out.println("Importando Entidade...");

		try {
			JAXBContext jaxbContext = JAXBContext
					.newInstance(com.viglet.turing.plugins.otca.af.xml.ObjectFactory.class);
			AFType documentType = ((JAXBElement<AFType>) jaxbContext.createUnmarshaller().unmarshal(inputStream))
					.getValue();

			TurNLPEntity turNLPEntity = this.setEntity(documentType.getName(), documentType.getDescription());
			this.setTerms(turNLPEntity, documentType.getTerms());

		} catch (JAXBException ejaxb) {
			ejaxb.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		URI absolutePath = uriInfo.getAbsolutePath();
		URI redirect = new URI(absolutePath.getScheme() + "://" + absolutePath.getHost() + ":" + absolutePath.getPort()
				+ "/turing/#entity/import");
		return Response.temporaryRedirect(redirect).build();
	}
}
