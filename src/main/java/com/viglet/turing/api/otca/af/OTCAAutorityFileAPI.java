package com.viglet.turing.api.otca.af;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
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
import javax.xml.bind.Unmarshaller;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import org.json.JSONException;

import com.viglet.turing.nlp.VigNLPRelationType;
import com.viglet.turing.nlp.VigNLPTermAccent;
import com.viglet.turing.nlp.VigNLPTermCase;
import com.viglet.turing.persistence.model.TurEntity;
import com.viglet.turing.persistence.model.VigTerm;
import com.viglet.turing.persistence.model.VigTermAttribute;
import com.viglet.turing.persistence.model.VigTermRelationFrom;
import com.viglet.turing.persistence.model.VigTermRelationTo;
import com.viglet.turing.persistence.model.VigTermVariation;
import com.viglet.turing.persistence.model.VigTermVariationLanguage;
import com.viglet.turing.plugins.otca.af.xml.AFAttributeDefType;
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
import com.viglet.util.VigUtils;

@Path("/otca/af")
public class OTCAAutorityFileAPI {
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
		s = VigUtils.stripAccents(s).toLowerCase().replaceAll("[^a-zA-Z0-9 ]", "").replaceAll(" ", "_");
		return s;
	}

	public TurEntity setEntity(String name, String description) {
		TurEntity turEntity = null;
		try {
			Query q = em.createQuery("SELECT e FROM TurEntity e where e.name = :name ").setParameter("name", name);
			if (q.getResultList().size() > 0) {
				turEntity = (TurEntity) q.getSingleResult();
			} else {
				if (turEntity == null) {
					turEntity = new TurEntity();
					turEntity.setName(name);
					if (description != null) {
						turEntity.setDescription(description);
					} else {
						turEntity.setDescription("");
					}
					turEntity.setInternalName(normalizeEntity(name));
					turEntity.setLocal(1);
					turEntity.setCollectionName(normalizeEntity(name));

					em.getTransaction().begin();
					em.persist(turEntity);
					em.getTransaction().commit();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return turEntity;
	}

	public void setTermAttribute(VigTerm vigTerm, Attributes attributes) {
		try {
			if (attributes != null) {
				for (AFAttributeType afAttributeType : attributes.getAttribute()) {
					for (String value : afAttributeType.getValues().getValue()) {
						VigTermAttribute vigTermAttribute = new VigTermAttribute();					
						vigTermAttribute.setValue(value);
						vigTermAttribute.setVigTerm(vigTerm);

						em.getTransaction().begin();
						em.persist(vigTermAttribute);
						em.getTransaction().commit();

					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setTermRelation(VigTerm vigTerm, Relations relations) {
		try {
			if (relations != null) {
				for (AFTermRelationType afTermRelationType : relations.getRelation()) {
					VigTermRelationFrom vigTermRelationFrom = new VigTermRelationFrom();
					if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.BT)) {
						vigTermRelationFrom.setRelationType(VigNLPRelationType.BT.id());
					} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.NT)) {
						vigTermRelationFrom.setRelationType(VigNLPRelationType.NT.id());
					} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.RT)) {
						vigTermRelationFrom.setRelationType(VigNLPRelationType.RT.id());
					} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.U)) {
						vigTermRelationFrom.setRelationType(VigNLPRelationType.U.id());
					} else if (afTermRelationType.getType().equals(AFTermRelationTypeEnum.UF)) {
						vigTermRelationFrom.setRelationType(VigNLPRelationType.UF.id());
					}
					vigTermRelationFrom.setVigTerm(vigTerm);

					em.getTransaction().begin();
					em.persist(vigTermRelationFrom);
					em.getTransaction().commit();

					VigTermRelationTo vigTermRelationTo = new VigTermRelationTo();
					vigTermRelationTo.setVigTermRelationFrom(vigTermRelationFrom);

					Query q = em.createQuery("SELECT t FROM VigTerm t where t.idCustom = :idCustom")
							.setParameter("idCustom", afTermRelationType.getId());
					VigTerm vigTermTo = null;
					if (q.getResultList().size() > 0) {
						vigTermTo = (VigTerm) q.getSingleResult();
					} else {
						vigTermTo = new VigTerm();

						vigTermTo.setIdCustom(afTermRelationType.getId());
						vigTermTo.setName(EMPTY_TERM_NAME);
						vigTermTo.setTurEntity(vigTermRelationFrom.getVigTerm().getVigEntity());
						em.getTransaction().begin();
						em.persist(vigTermTo);
						em.getTransaction().commit();
					}
					vigTermRelationTo.setVigTerm(vigTermTo);
					em.getTransaction().begin();
					em.persist(vigTermRelationTo);
					em.getTransaction().commit();

				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public void setTermVariation(VigTerm vigTerm, Variations variations) {
		try {
			for (AFTermVariationType afTermVariationType : variations.getVariation()) {
				VigTermVariation vigTermVariation = new VigTermVariation();
				vigTermVariation.setName(VigUtils.removeDuplicateWhiteSpaces(afTermVariationType.getName()));
				vigTermVariation.setNameLower(VigUtils.stripAccents(vigTermVariation.getName()).toLowerCase());				
				if (afTermVariationType.getAccent().equals(AFTermVariationAccentEnum.AI))
					vigTermVariation.setRuleAccent(VigNLPTermAccent.AI.id());
				else
					vigTermVariation.setRuleAccent(VigNLPTermAccent.AS.id());

				if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.CI))
					vigTermVariation.setRuleCase(VigNLPTermCase.CI.id());
				else if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.CS))
					vigTermVariation.setRuleCase(VigNLPTermCase.CS.id());
				else if (afTermVariationType.getCase().equals(AFTermVariationCaseEnum.UCS))
					vigTermVariation.setRuleCase(VigNLPTermCase.UCS.id());

				if (afTermVariationType.getPrefix() != null) {
					vigTermVariation.setRulePrefix(afTermVariationType.getPrefix().getValue());
					vigTermVariation.setRulePrefixRequired(afTermVariationType.getPrefix().isRequired() ? 1 : 0);
				}
				if (afTermVariationType.getSuffix() != null) {
					vigTermVariation.setRuleSuffix(afTermVariationType.getSuffix().getValue());
					vigTermVariation.setRuleSuffixRequired(afTermVariationType.getSuffix().isRequired() ? 1 : 0);
				}
				vigTermVariation.setWeight(afTermVariationType.getWeight());
				vigTermVariation.setVigTerm(vigTerm);

				em.getTransaction().begin();
				em.persist(vigTermVariation);
				em.getTransaction().commit();

				for (String language : afTermVariationType.getLanguages().getLanguage()) {
					VigTermVariationLanguage vigTermVariationLanguage = new VigTermVariationLanguage();
					vigTermVariationLanguage.setLanguage(language);
					vigTermVariationLanguage.setVigTerm(vigTerm);
					vigTermVariationLanguage.setVigTermVariation(vigTermVariation);
					em.getTransaction().begin();
					em.persist(vigTermVariationLanguage);
					em.getTransaction().commit();

				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setTerms(TurEntity turEntity, Terms terms) {
		boolean overwrite = false;
		try {
			for (AFTermType afTermType : terms.getTerm()) {
				String termId = afTermType.getId();
				Query q = em.createQuery("SELECT t FROM VigTerm t where t.idCustom = :idCustom")
						.setParameter("idCustom", termId);
				VigTerm vigTerm = null;

				if (q.getResultList().size() == 1) {
					vigTerm = (VigTerm) q.getSingleResult();
					// Term that was created during relation but the parent
					// wasn't created yet
					overwrite = vigTerm.getName().equals(EMPTY_TERM_NAME) ? true : false;
				} else {
					vigTerm = new VigTerm();
					overwrite = true;
				}

				if (overwrite) {
					vigTerm.setIdCustom(termId);
					vigTerm.setName(afTermType.getName());
					vigTerm.setTurEntity(turEntity);

					em.getTransaction().begin();
					em.persist(vigTerm);
					em.getTransaction().commit();

					this.setTermVariation(vigTerm, afTermType.getVariations());
					this.setTermRelation(vigTerm, afTermType.getRelations());
					this.setTermAttribute(vigTerm, afTermType.getAttributes());
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

			TurEntity turEntity = this.setEntity(documentType.getName(), documentType.getDescription());
			this.setTerms(turEntity, documentType.getTerms());

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
