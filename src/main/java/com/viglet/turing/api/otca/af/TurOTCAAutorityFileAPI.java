package com.viglet.turing.api.otca.af;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
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
	EntityManager em = null;
	final String EMPTY_TERM_NAME = "<EMPTY>";

	public void init() {
		try {
			String PERSISTENCE_UNIT_NAME = "semantics-app";
			EntityManagerFactory factory;

			factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
			em = factory.createEntityManager();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String normalizeEntity(String s) {
		s = TurUtils.stripAccents(s).toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").replaceAll(" ", "_");
		return s;
	}

	public TurNLPEntity setEntity(String name, String description) {
		TurNLPEntity turNLPEntity = null;
		try {
			Query q = em.createQuery("SELECT e FROM TurNLPEntity e where e.name = :name ").setParameter("name", name);
			if (q.getResultList().size() > 0) {
				turNLPEntity = (TurNLPEntity) q.getSingleResult();
			} else {
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

					em.getTransaction().begin();
					em.persist(turNLPEntity);
					em.getTransaction().commit();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return turNLPEntity;
	}

	public void setTermAttribute(TurTerm turTerm, Attributes attributes) {
		try {
			if (attributes != null) {
				for (AFAttributeType afAttributeType : attributes.getAttribute()) {
					for (String value : afAttributeType.getValues().getValue()) {
						TurTermAttribute turTermAttribute = new TurTermAttribute();					
						turTermAttribute.setValue(value);
						turTermAttribute.setTurTerm(turTerm);

						em.getTransaction().begin();
						em.persist(turTermAttribute);
						em.getTransaction().commit();

					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setTermRelation(TurTerm turTerm, Relations relations) {
		try {
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

					em.getTransaction().begin();
					em.persist(turTermRelationFrom);
					em.getTransaction().commit();

					TurTermRelationTo turTermRelationTo = new TurTermRelationTo();
					turTermRelationTo.setTurTermRelationFrom(turTermRelationFrom);

					Query q = em.createQuery("SELECT t FROM TurTerm t where t.idCustom = :idCustom")
							.setParameter("idCustom", afTermRelationType.getId());
					TurTerm turTermTo = null;
					if (q.getResultList().size() > 0) {
						turTermTo = (TurTerm) q.getSingleResult();
					} else {
						turTermTo = new TurTerm();

						turTermTo.setIdCustom(afTermRelationType.getId());
						turTermTo.setName(EMPTY_TERM_NAME);
						turTermTo.setTurNLPEntity(turTermRelationFrom.getTurTerm().getTurNLPEntity());
						em.getTransaction().begin();
						em.persist(turTermTo);
						em.getTransaction().commit();
					}
					turTermRelationTo.setTurTerm(turTermTo);
					em.getTransaction().begin();
					em.persist(turTermRelationTo);
					em.getTransaction().commit();

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void setTermVariation(TurTerm turTerm, Variations variations) {
		try {
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

				em.getTransaction().begin();
				em.persist(turTermVariation);
				em.getTransaction().commit();

				for (String language : afTermVariationType.getLanguages().getLanguage()) {
					TurTermVariationLanguage turTermVariationLanguage = new TurTermVariationLanguage();
					turTermVariationLanguage.setLanguage(language);
					turTermVariationLanguage.setTurTerm(turTerm);
					turTermVariationLanguage.setTurTermVariation(turTermVariation);
					em.getTransaction().begin();
					em.persist(turTermVariationLanguage);
					em.getTransaction().commit();

				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setTerms(TurNLPEntity turNLPEntity, Terms terms) {
		boolean overwrite = false;
		try {
			for (AFTermType afTermType : terms.getTerm()) {
				String termId = afTermType.getId();
				Query q = em.createQuery("SELECT t FROM TurTerm t where t.idCustom = :idCustom")
						.setParameter("idCustom", termId);
				TurTerm turTerm = null;

				if (q.getResultList().size() == 1) {
					turTerm = (TurTerm) q.getSingleResult();
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

					em.getTransaction().begin();
					em.persist(turTerm);
					em.getTransaction().commit();

					this.setTermVariation(turTerm, afTermType.getVariations());
					this.setTermRelation(turTerm, afTermType.getRelations());
					this.setTermAttribute(turTerm, afTermType.getAttributes());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
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
		this.init();

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
