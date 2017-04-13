package com.viglet.turing.plugins.otca;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;

import com.viglet.turing.nlp.VigNLPResults;
import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.persistence.model.VigServicesNLPEntity;
import com.viglet.turing.plugins.nlp.NLPImpl;
import com.viglet.turing.plugins.otca.af.xml.AFType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseCategorizerResultCategoryType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseCategorizerResultKnowledgeBaseType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseCategorizerResultType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseConceptExtractorResultConcept1Type;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseConceptExtractorResultConcept2Type;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseConceptExtractorResultType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultExtractResultType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultFullTextSearchResultType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultTermOccurenceType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultTermParentType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultTermParentType.Parents;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultTermType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseEntityExtractorResultType;
import com.viglet.turing.plugins.otca.response.xml.ServerResponseType;

public class TmeConnector implements NLPImpl {
	List<VigServicesNLPEntity> nlpEntities = null;
	Map<String, JSONArray> hmEntities = new HashMap<String, JSONArray>();
	VigService vigService = null;
	public JSONObject json;
	public static int PRETTY_PRINT_INDENT_FACTOR = 4;

	private static String request = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<Nserver>" + "       <Methods>"
			+ "               <Ping/>" + "       </Methods>" + "</Nserver>";

	@SuppressWarnings("unchecked")
	public TmeConnector(VigService vigService) {
		this.vigService = vigService;
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryNLPEntity = em
				.createQuery(
						"SELECT sne FROM VigServicesNLPEntity sne, VigService s where s.id = :id_service and sne.vigService = s and sne.enabled = :enabled ")
				.setParameter("id_service", vigService.getId()).setParameter("enabled", 1);

		nlpEntities = queryNLPEntity.getResultList();
	}

	/**
	 * Send XML request to TME
	 * 
	 * @param request
	 *            XML request
	 * @return XML response
	 */
	public String request(VigService vigService, String request) {
		try {
			if (request == null) {
				return null;
			}
			byte[] data = request.trim().getBytes("UTF-8");
			int length = data.length;
			if (length == 0) {
				return null;
			}
			Socket socket = new Socket(vigService.getHost(), vigService.getPort());
			OutputStream output = socket.getOutputStream();
			InputStream input = socket.getInputStream();
			try {
				// Header (4 bytes little-endian data length)
				byte[] header = new byte[4];
				header[3] = (byte) ((length & 0xFF000000) >> 24);
				header[2] = (byte) ((length & 0x00FF0000) >> 16);
				header[1] = (byte) ((length & 0x0000FF00) >> 8);
				header[0] = (byte) (length & 0x000000FF);
				output.write(header);
				output.write(data);
				output.flush();
				// Retrieve response length
				int b = input.read();
				length = (b & 0xFF);
				b = input.read();
				length |= ((b & 0xFF) << 8);
				b = input.read();
				length |= ((b & 0xFF) << 16);
				b = input.read();
				length |= ((b & 0xFF) << 24);
				if (length <= 0) {
					return null;
				}
				// Retrieve response content
				data = new byte[length];
				for (int pos = 0, size = 0; length > 0; pos += size, length -= size) {
					size = input.read(data, pos, length);
					if (size == -1) {
						throw (new RuntimeException("Invalid response"));
					}
				}
				return new String(data, "UTF-8");
			} finally {
				output.close();
				input.close();
				socket.close();
			}
		} catch (Exception e) {
			System.out.println("Server not reached: " + e.getMessage());
			return null;
		}
	}

	public VigNLPResults retrieve(String text) throws TransformerException, Exception {

		StringBuilder sb = new StringBuilder();
		sb.append("<Nserver>");
		sb.append("<Methods>");
		sb.append("<nconceptextractor>");
		sb.append("<ComplexConcepts>");
		sb.append("<RelevancyLevel>FIRST</RelevancyLevel>");
		sb.append("<NumberOfComplexConcepts>40</NumberOfComplexConcepts>");
		sb.append("</ComplexConcepts>");
		sb.append("<SimpleConcepts>");
		sb.append("<NumberOfSimpleConcepts>20</NumberOfSimpleConcepts>");
		sb.append("</SimpleConcepts>");
		sb.append("<ExcludeEntities/>");
		sb.append("<ResultLayout>NCONCEPTEXTRACTOR</ResultLayout>");
		sb.append("</nconceptextractor>");
		sb.append("<nfinder>");
		sb.append("<nfExtract>");
		sb.append("<Cartridges>");

		for (VigServicesNLPEntity entity : nlpEntities) {
			sb.append("<Cartridge>" + entity.getName() + "</Cartridge>");
		}

		sb.append("</Cartridges>");
		sb.append("<OutputAttributes>true</OutputAttributes>");
		sb.append("<OutputParents>true</OutputParents>");
		sb.append("</nfExtract>");

		sb.append("<nfFullTextSearch>");
		sb.append("<Cartridges>");
		sb.append("<Cartridge>APICULTURA</Cartridge>");
		sb.append(" <Cartridge>CONFECÇÃO</Cartridge>");
		sb.append("<Cartridge>ARTESANATO</Cartridge>");
		sb.append(" <Cartridge>AQUICULTURA</Cartridge>");
		sb.append("<Cartridge>COURO E CALÇADOS</Cartridge>");
		sb.append("<Cartridge>CONSTRUÇÃO CIVIL</Cartridge>");
		sb.append("<Cartridge>ALIMENTO</Cartridge>");
		sb.append("<Cartridge>ROCHA ORNAMENTAL</Cartridge>");
		sb.append("<Cartridge>AGRICULTURA</Cartridge>");
		sb.append("<Cartridge>GRÁFICA E EDITORA</Cartridge>");
		sb.append("<Cartridge>TURISMO</Cartridge>");
		sb.append("<Cartridge>TÊXTIL</Cartridge>");
		sb.append("<Cartridge>CERÂMICA</Cartridge>");
		sb.append("<Cartridge>BEBIDA</Cartridge>");
		sb.append("<Cartridge>CULTURA, ENTRETENIMENTO E LAZER</Cartridge>");
		sb.append("<Cartridge>RECICLAGEM</Cartridge>");
		sb.append("<Cartridge>PLÁSTICO E BORRACHA</Cartridge>");
		sb.append("<Cartridge>METALURGIA, METAL MECÂNICA E AUTO PEÇAS</Cartridge>");
		sb.append("<Cartridge>HIGIENE PESSOAL, PERFUMARIA E COSMÉTICO</Cartridge>");
		sb.append("<Cartridge>ELETRO-ELETRÔNICA</Cartridge>");
		sb.append("<Cartridge>PETRÓLEO, GÁS E ENERGIA</Cartridge>");
		sb.append("<Cartridge>QUÍMICA</Cartridge>");
		sb.append("<Cartridge>GEMA, JÓIA E BIJUTERIA</Cartridge>");
		sb.append("<Cartridge>EMPREENDEDORISMO</Cartridge>");
		sb.append("<Cartridge>GESTÃO EMPRESARIAL</Cartridge>");
		sb.append("<Cartridge>AMBIENTE ECONÔMICO E SOCIAL</Cartridge>");
		sb.append("<Cartridge>COMÉRCIO VAREJISTA E ATACADISTA</Cartridge>");
		sb.append("<Cartridge>SAÚDE, BELEZA E MEDICINA</Cartridge>");
		sb.append("<Cartridge>BIOTECNOLOGIA</Cartridge>");
		sb.append("<Cartridge>PECUÁRIA</Cartridge>");
		sb.append("<Cartridge>MADEIRA E MOBILIÁRIO</Cartridge>");
		sb.append("<Cartridge>AMBIENTE NORMATIVO</Cartridge>");
		sb.append("<Cartridge>TECNOLOGIA DA INFORMAÇÃO E COMUNICAÇÃO</Cartridge>");
		sb.append("<Cartridge>TABELAS AUXILIARES</Cartridge>");
		sb.append("<Cartridge>VITIVINICULTURA</Cartridge>");
		sb.append("<Cartridge>OUTROS</Cartridge>");
		sb.append("</Cartridges>");
		sb.append("<OutputAttributes>true</OutputAttributes>");
		sb.append("<OutputParents>true</OutputParents>");
		sb.append("</nfFullTextSearch>");

		sb.append("</nfinder>");
		sb.append("<ncategorizer>");
		sb.append("<KnowledgeBase>");
		sb.append("<KBid>IPTC</KBid>");
		sb.append("</KnowledgeBase>");
		sb.append("</ncategorizer>");
		sb.append("</Methods>");
		sb.append("<Texts>");
		sb.append("<Text>");
		sb.append("<TextID>32534ae2e9282510VgnVCM1000004c00210aRCRD</TextID>");
		sb.append("<LanguageID>PORTUGUESE</LanguageID>");
		sb.append("<NSTEIN_Text>");
		sb.append(text);
		sb.append("</NSTEIN_Text>");
		sb.append("</Text>");
		sb.append("</Texts>");
		sb.append("</Nserver>");

		this.toJSON(this.request(vigService, sb.toString()));

		VigNLPResults vigNLPResults = new VigNLPResults();
		vigNLPResults.setJsonResult(this.getJSON());
		vigNLPResults.setVigNLPServicesEntity(nlpEntities);

		return vigNLPResults;

	}

	public String toJSON(String xml) {
		String jsonResult = null;

		InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

		JAXBContext jaxbContext;
		try {
			System.out.println(xml);
			jaxbContext = JAXBContext.newInstance(com.viglet.turing.plugins.otca.response.xml.ObjectFactory.class);
			ServerResponseType serverResponseType = ((JAXBElement<ServerResponseType>) jaxbContext.createUnmarshaller()
					.unmarshal(is)).getValue();
			System.out.println("getErrorDescription: " + serverResponseType.getErrorDescription());
			System.out.println("getVersion: " + serverResponseType.getVersion());
			for (Object result : serverResponseType.getResults().getPingOrGetSupportedEncodingsOrLanguagedetector()) {
				this.getResult(result);
			}
			for (Object result : serverResponseType.getResults()
					.getLanguageDetectorOrNConceptExtractorOrNCategorizer()) {
				this.getResult(result);
			}

		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			this.json = XML.toJSONObject(xml);

			String jsonPrettyPrintString = json.toString(PRETTY_PRINT_INDENT_FACTOR);
			jsonResult = jsonPrettyPrintString;
			// System.out.println(jsonResult);
		} catch (JSONException je) {
			je.printStackTrace();
		}
		return jsonResult;
	}

	public  void getParentTerms(Parents parents, Map<String, JSONArray> hmEntities, String cartridgeID) {
		if (parents != null) {
			for (ServerResponseEntityExtractorResultTermParentType parent : parents.getParent()) {
				hmEntities.get(cartridgeID).put(parent.getTerm());	
				this.getParentTerms(parent.getParents(), hmEntities, cartridgeID);
			}
		}
		
	}
	public void getResult(Object result) {

		System.out.println("iterateResults... " + result.toString());

		if (result instanceof ServerResponseConceptExtractorResultType) {
			ServerResponseConceptExtractorResultType concepts = (ServerResponseConceptExtractorResultType) result;
			System.out.println("Concepts:" + concepts.getName());
			if (concepts.getComplexConcepts() != null) {
				System.out.println("ComplexConcept");
				if (!hmEntities.containsKey("ComplexConcepts")) {
					hmEntities.put("ComplexConcepts", new JSONArray());
				}

				for (Object complexConcept : concepts.getComplexConcepts().getConceptOrExtractedTerm()) {
					if (complexConcept instanceof ServerResponseConceptExtractorResultConcept1Type) {
						hmEntities.get("ComplexConcepts")
								.put(((ServerResponseConceptExtractorResultConcept1Type) complexConcept).getValue());
						System.out.println("ComplexConcept: "
								+ ((ServerResponseConceptExtractorResultConcept1Type) complexConcept).getValue());
					}
					if (complexConcept instanceof ServerResponseConceptExtractorResultConcept2Type) {
						hmEntities.get("ComplexConcepts")
								.put(((ServerResponseConceptExtractorResultConcept2Type) complexConcept).getContent());
						System.out.println("ComplexConcept: "
								+ ((ServerResponseConceptExtractorResultConcept2Type) complexConcept).getContent());
					}
				}
			}
			if (concepts.getSimpleConcepts() != null) {
				System.out.println("SimpleConcept");
				if (!hmEntities.containsKey("SimpleConcepts")) {
					hmEntities.put("SimpleConcepts", new JSONArray());
				}
				for (Object simpleConcepts : concepts.getSimpleConcepts().getConceptOrExtractedTerm()) {
					if (simpleConcepts instanceof ServerResponseConceptExtractorResultConcept1Type) {
						hmEntities.get("SimpleConcepts")
								.put(((ServerResponseConceptExtractorResultConcept1Type) simpleConcepts).getValue());
					}
					if (simpleConcepts instanceof ServerResponseConceptExtractorResultConcept2Type) {
						hmEntities.get("SimpleConcepts")
								.put(((ServerResponseConceptExtractorResultConcept2Type) simpleConcepts).getContent());
						System.out.println("SimpleConcept: "
								+ ((ServerResponseConceptExtractorResultConcept2Type) simpleConcepts).getContent());
					}
				}
			}
		} else if (result instanceof ServerResponseEntityExtractorResultType) {
			ServerResponseEntityExtractorResultType entities = (ServerResponseEntityExtractorResultType) result;
			for (Object nf : entities.getNfExtractOrNfFullTextSearch()) {
				if (nf instanceof ServerResponseEntityExtractorResultExtractResultType) {
					ServerResponseEntityExtractorResultExtractResultType extractor = (ServerResponseEntityExtractorResultExtractResultType) nf;
					for (ServerResponseEntityExtractorResultTermType term : extractor.getExtractedTerm()) {
						System.out.println("getCartridgeID: " + term.getCartridgeID());
						if (!hmEntities.containsKey(term.getCartridgeID())) {
							hmEntities.put(term.getCartridgeID(), new JSONArray());
						}

						System.out.println("getId: " + term.getId());
						System.out.println("getNfinderNormalized: " + term.getNfinderNormalized());
						if (term.getMainTerm() != null) {
							// System.out.println("getMainTerm: " +
							// term.getMainTerm().getValue());
						}
						if (term.getSubterms() != null) {
							for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms()
									.getSubterm()) {
								hmEntities.get(term.getCartridgeID()).put(subterm.getValue());
								// System.out.println("getSubTerm: " +
								// subterm.getValue());
							}

						}
					}
				} else if (nf instanceof ServerResponseEntityExtractorResultFullTextSearchResultType) {
					ServerResponseEntityExtractorResultFullTextSearchResultType fullText = (ServerResponseEntityExtractorResultFullTextSearchResultType) nf;
					for (ServerResponseEntityExtractorResultTermType term : fullText.getExtractedTerm()) {
						if (!hmEntities.containsKey(term.getCartridgeID())) {
							hmEntities.put(term.getCartridgeID(), new JSONArray());
						}
						if (term.getMainTerm() != null) {
							// System.out.println("getMainTerm: " +
							// term.getMainTerm().getValue());
						}
						if (term.getSubterms() != null) {
							for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms()
									.getSubterm()) {
								hmEntities.get(term.getCartridgeID()).put(subterm.getValue());
								// System.out.println("getSubTerm: " +
								// subterm.getValue());
							}

						}
						if (term.getHierarchy() != null && term.getHierarchy().getBase() != null
								&& term.getHierarchy().getBase().getParents() != null) {
							
							for (ServerResponseEntityExtractorResultTermParentType parent : term.getHierarchy()
									.getBase().getParents().getParent()) {
								hmEntities.get(term.getCartridgeID()).put(parent.getTerm());					
								this.getParentTerms(parent.getParents(), hmEntities, term.getCartridgeID());
							}
						}
					}
				}

			}
		} else if (result instanceof ServerResponseCategorizerResultType) {
			ServerResponseCategorizerResultType categorizer = (ServerResponseCategorizerResultType) result;
			if (categorizer.getCategories() != null) {
				for (ServerResponseCategorizerResultCategoryType category : categorizer.getCategories().getCategory()) {
					for (Serializable content : category.getContent()) {
						System.out.println("Category Content: " + content.toString());
					}
					System.out.println("Category ID: " + category.getId());
					System.out.println("Category Weight: " + category.getWeight());
				}
			}
			if (categorizer.getKnowledgeBase() != null) {
				for (ServerResponseCategorizerResultKnowledgeBaseType kb : categorizer.getKnowledgeBase()) {
					if (!hmEntities.containsKey(kb.getKBid())) {
						hmEntities.put(kb.getKBid(), new JSONArray());
					}
					// System.out.println("KB Id:" + kb.getKBid());
					// System.out.println("KB Relevance:" +
					// kb.getRelevancyScore());
					if (kb.getCategories() != null) {
						for (ServerResponseCategorizerResultCategoryType category : kb.getCategories().getCategory()) {

							for (Serializable content : category.getContent()) {
								System.out.println("KB Content: " + content.toString());
								hmEntities.get(kb.getKBid())
										.put(content.toString().replaceAll(category.getId() + " - ", ""));
							}
							// System.out.println("KB ID: " + category.getId());
							// System.out.println("KB Weight: " +
							// category.getWeight());
						}
					}
					if (kb.getRejectedCategories() != null) {
						for (ServerResponseCategorizerResultCategoryType category : kb.getRejectedCategories()
								.getRejectedCategory()) {
							for (Serializable content : category.getContent()) {
								System.out.println("KB Content Rejected: " + content.toString());
							}
							System.out.println("KB ID Rejected: " + category.getId());
							System.out.println("KB Weight Rejected: " + category.getWeight());

						}
					}
				}

			}

		}

	}

	public JSONObject getJSON() {
		JSONObject jsonObject = new JSONObject();

		for (VigServicesNLPEntity entity : nlpEntities) {
			jsonObject.put(entity.getVigEntity().getCollectionName(), hmEntities.get(entity.getName()));
		}
		jsonObject.put("nlp", "OTCA");
		return jsonObject;
	}

}
