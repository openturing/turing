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

package com.viglet.turing.plugins.otca;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
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
import com.viglet.turing.solr.TurSolrField;

@Component
public class TurTMEConnector implements TurNLPImpl {
	static final Logger logger = LogManager.getLogger(TurTMEConnector.class.getName());
	@Autowired
	TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;
	@Autowired
	TurSolrField turSolrField;

	List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	Map<String, List<Object>> hmEntities = new HashMap<String,  List<Object>>();
	TurNLPInstance turNLPInstance = null;
	public JSONObject json;
	public static int PRETTY_PRINT_INDENT_FACTOR = 4;

	public void startup(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;

		nlpInstanceEntities = turNLPInstanceEntityRepository.findByTurNLPInstanceAndEnabled(turNLPInstance, 1);
	}

	/**
	 * Send XML request to TME
	 * 
	 * @param request
	 *            XML request
	 * @return XML response
	 */
	public String request(TurNLPInstance turNLPInstance, String request) {
		try {
			if (request == null) {
				return null;
			}
			byte[] data = request.trim().getBytes("UTF-8");
			int length = data.length;
			if (length == 0) {
				return null;
			}
			Socket socket = new Socket(turNLPInstance.getHost(), turNLPInstance.getPort());
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
			logger.debug("Server not reached: " + e.getMessage());
			return null;
		}
	}

	public Map<String, Object> retrieve(Map<String, Object> attributes) throws TransformerException, Exception {

		for (Object attrValue : attributes.values()) {
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

			for (TurNLPInstanceEntity entity : nlpInstanceEntities) {
				sb.append("<Cartridge>" + entity.getName() + "</Cartridge>");
			}

			sb.append("</Cartridges>");
			sb.append("<OutputAttributes>true</OutputAttributes>");
			sb.append("<OutputParents>true</OutputParents>");
			sb.append("</nfExtract>");

			sb.append("<nfFullTextSearch>");
			sb.append("<Cartridges>");
			sb.append("<Cartridge>APICULTURA</Cartridge>");
			sb.append(" <Cartridge>CONFECÃ‡ÃƒO</Cartridge>");
			sb.append("<Cartridge>ARTESANATO</Cartridge>");
			sb.append(" <Cartridge>AQUICULTURA</Cartridge>");
			sb.append("<Cartridge>COURO E CALÃ‡ADOS</Cartridge>");
			sb.append("<Cartridge>CONSTRUÃ‡ÃƒO CIVIL</Cartridge>");
			sb.append("<Cartridge>ALIMENTO</Cartridge>");
			sb.append("<Cartridge>ROCHA ORNAMENTAL</Cartridge>");
			sb.append("<Cartridge>AGRICULTURA</Cartridge>");
			sb.append("<Cartridge>GRÃ�FICA E EDITORA</Cartridge>");
			sb.append("<Cartridge>TURISMO</Cartridge>");
			sb.append("<Cartridge>TÃŠXTIL</Cartridge>");
			sb.append("<Cartridge>CERÃ‚MICA</Cartridge>");
			sb.append("<Cartridge>BEBIDA</Cartridge>");
			sb.append("<Cartridge>CULTURA, ENTRETENIMENTO E LAZER</Cartridge>");
			sb.append("<Cartridge>RECICLAGEM</Cartridge>");
			sb.append("<Cartridge>PLÃ�STICO E BORRACHA</Cartridge>");
			sb.append("<Cartridge>METALURGIA, METAL MECÃ‚NICA E AUTO PEÃ‡AS</Cartridge>");
			sb.append("<Cartridge>HIGIENE PESSOAL, PERFUMARIA E COSMÃ‰TICO</Cartridge>");
			sb.append("<Cartridge>ELETRO-ELETRÃ”NICA</Cartridge>");
			sb.append("<Cartridge>PETRÃ“LEO, GÃ�S E ENERGIA</Cartridge>");
			sb.append("<Cartridge>QUÃ�MICA</Cartridge>");
			sb.append("<Cartridge>GEMA, JÃ“IA E BIJUTERIA</Cartridge>");
			sb.append("<Cartridge>EMPREENDEDORISMO</Cartridge>");
			sb.append("<Cartridge>GESTÃƒO EMPRESARIAL</Cartridge>");
			sb.append("<Cartridge>AMBIENTE ECONÃ”MICO E SOCIAL</Cartridge>");
			sb.append("<Cartridge>COMÃ‰RCIO VAREJISTA E ATACADISTA</Cartridge>");
			sb.append("<Cartridge>SAÃšDE, BELEZA E MEDICINA</Cartridge>");
			sb.append("<Cartridge>BIOTECNOLOGIA</Cartridge>");
			sb.append("<Cartridge>PECUÃ�RIA</Cartridge>");
			sb.append("<Cartridge>MADEIRA E MOBILIÃ�RIO</Cartridge>");
			sb.append("<Cartridge>AMBIENTE NORMATIVO</Cartridge>");
			sb.append("<Cartridge>TECNOLOGIA DA INFORMAÃ‡ÃƒO E COMUNICAÃ‡ÃƒO</Cartridge>");
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
			sb.append(turSolrField.convertFieldToString(attrValue));
			sb.append("</NSTEIN_Text>");
			sb.append("</Text>");
			sb.append("</Texts>");
			sb.append("</Nserver>");

			this.toJSON(this.request(turNLPInstance, sb.toString()));
		}

		return this.getAttributes();

	}

	public String toJSON(String xml) {
		String jsonResult = null;

		InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

		JAXBContext jaxbContext;
		try {
			logger.debug(xml);
			jaxbContext = JAXBContext.newInstance(com.viglet.turing.plugins.otca.response.xml.ObjectFactory.class);
			@SuppressWarnings("unchecked")
			ServerResponseType serverResponseType = ((JAXBElement<ServerResponseType>) jaxbContext.createUnmarshaller()
					.unmarshal(is)).getValue();
			logger.debug("getErrorDescription: " + serverResponseType.getErrorDescription());
			logger.debug("getVersion: " + serverResponseType.getVersion());
			for (Object result : serverResponseType.getResults().getPingOrGetSupportedEncodingsOrLanguagedetector()) {
				this.getResult(result);
			}
			for (Object result : serverResponseType.getResults()
					.getLanguageDetectorOrNConceptExtractorOrNCategorizer()) {
				this.getResult(result);
			}

		} catch (JAXBException e) {
			logger.error(e);
		}
		try {
			this.json = XML.toJSONObject(xml);

			String jsonPrettyPrintString = json.toString(PRETTY_PRINT_INDENT_FACTOR);
			jsonResult = jsonPrettyPrintString;
			// System.out.println(jsonResult);
		} catch (JSONException e) {
			logger.error(e);
		}
		return jsonResult;
	}

	public void getParentTerms(Parents parents, Map<String, List<Object>> hmEntities, String cartridgeID) {
		if (parents != null) {
			for (ServerResponseEntityExtractorResultTermParentType parent : parents.getParent()) {
				hmEntities.get(cartridgeID).add(parent.getTerm());
				this.getParentTerms(parent.getParents(), hmEntities, cartridgeID);
			}
		}

	}

	public void getResult(Object result) {

		logger.debug("iterateResults... " + result.toString());

		if (result instanceof ServerResponseConceptExtractorResultType) {
			ServerResponseConceptExtractorResultType concepts = (ServerResponseConceptExtractorResultType) result;
			logger.debug("Concepts:" + concepts.getName());
			if (concepts.getComplexConcepts() != null) {
				logger.debug("ComplexConcept");
				if (!hmEntities.containsKey("ComplexConcepts")) {
					hmEntities.put("ComplexConcepts", new ArrayList<Object>());
				}

				for (Object complexConcept : concepts.getComplexConcepts().getConceptOrExtractedTerm()) {
					if (complexConcept instanceof ServerResponseConceptExtractorResultConcept1Type) {
						hmEntities.get("ComplexConcepts")
								.add(((ServerResponseConceptExtractorResultConcept1Type) complexConcept).getValue());
						logger.debug("ComplexConcept: "
								+ ((ServerResponseConceptExtractorResultConcept1Type) complexConcept).getValue());
					}
					if (complexConcept instanceof ServerResponseConceptExtractorResultConcept2Type) {
						hmEntities.get("ComplexConcepts")
								.add(((ServerResponseConceptExtractorResultConcept2Type) complexConcept).getContent());
						logger.debug("ComplexConcept: "
								+ ((ServerResponseConceptExtractorResultConcept2Type) complexConcept).getContent());
					}
				}
			}
			if (concepts.getSimpleConcepts() != null) {
				logger.debug("SimpleConcept");
				if (!hmEntities.containsKey("SimpleConcepts")) {
					hmEntities.put("SimpleConcepts", new ArrayList<Object>());
				}
				for (Object simpleConcepts : concepts.getSimpleConcepts().getConceptOrExtractedTerm()) {
					if (simpleConcepts instanceof ServerResponseConceptExtractorResultConcept1Type) {
						hmEntities.get("SimpleConcepts")
								.add(((ServerResponseConceptExtractorResultConcept1Type) simpleConcepts).getValue());
					}
					if (simpleConcepts instanceof ServerResponseConceptExtractorResultConcept2Type) {
						hmEntities.get("SimpleConcepts")
								.add(((ServerResponseConceptExtractorResultConcept2Type) simpleConcepts).getContent());
						logger.debug("SimpleConcept: "
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
						logger.debug("getCartridgeID: " + term.getCartridgeID());
						if (!hmEntities.containsKey(term.getCartridgeID())) {
							hmEntities.put(term.getCartridgeID(), new ArrayList<Object>());
						}

						logger.debug("getId: " + term.getId());
						logger.debug("getNfinderNormalized: " + term.getNfinderNormalized());
						if (term.getMainTerm() != null) {
							// System.out.println("getMainTerm: " +
							// term.getMainTerm().getValue());
						}
						if (term.getSubterms() != null) {
							for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms()
									.getSubterm()) {
								hmEntities.get(term.getCartridgeID()).add(subterm.getValue());
								// System.out.println("getSubTerm: " +
								// subterm.getValue());
							}

						}
					}
				} else if (nf instanceof ServerResponseEntityExtractorResultFullTextSearchResultType) {
					ServerResponseEntityExtractorResultFullTextSearchResultType fullText = (ServerResponseEntityExtractorResultFullTextSearchResultType) nf;
					for (ServerResponseEntityExtractorResultTermType term : fullText.getExtractedTerm()) {
						if (!hmEntities.containsKey(term.getCartridgeID())) {
							hmEntities.put(term.getCartridgeID(), new ArrayList<Object>());
						}
						if (term.getMainTerm() != null) {
							// System.out.println("getMainTerm: " +
							// term.getMainTerm().getValue());
						}
						if (term.getSubterms() != null) {
							for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms()
									.getSubterm()) {
								hmEntities.get(term.getCartridgeID()).add(subterm.getValue());
								// System.out.println("getSubTerm: " +
								// subterm.getValue());
							}

						}
						if (term.getHierarchy() != null && term.getHierarchy().getBase() != null
								&& term.getHierarchy().getBase().getParents() != null) {

							for (ServerResponseEntityExtractorResultTermParentType parent : term.getHierarchy()
									.getBase().getParents().getParent()) {
								hmEntities.get(term.getCartridgeID()).add(parent.getTerm());
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
						logger.debug("Category Content: " + content.toString());
					}
					logger.debug("Category ID: " + category.getId());
					logger.debug("Category Weight: " + category.getWeight());
				}
			}
			if (categorizer.getKnowledgeBase() != null) {
				for (ServerResponseCategorizerResultKnowledgeBaseType kb : categorizer.getKnowledgeBase()) {
					if (!hmEntities.containsKey(kb.getKBid())) {
						hmEntities.put(kb.getKBid(), new ArrayList<Object>());
					}
					// System.out.println("KB Id:" + kb.getKBid());
					// System.out.println("KB Relevance:" +
					// kb.getRelevancyScore());
					if (kb.getCategories() != null) {
						for (ServerResponseCategorizerResultCategoryType category : kb.getCategories().getCategory()) {

							for (Serializable content : category.getContent()) {
								logger.debug("KB Content: " + content.toString());
								hmEntities.get(kb.getKBid())
										.add(content.toString().replaceAll(category.getId() + " - ", ""));
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
								logger.debug("KB Content Rejected: " + content.toString());
							}
							logger.debug("KB ID Rejected: " + category.getId());
							logger.debug("KB Weight Rejected: " + category.getWeight());

						}
					}
				}

			}

		}

	}

	public Map<String, Object> getAttributes() throws JSONException {
		logger.debug("getAttributes() hmEntities: " + hmEntities.toString());
		logger.debug("getAttributes() nlpInstanceEntities: " + nlpInstanceEntities.toString());
		Map<String, Object> entityAttributes = new HashMap<String, Object>();

		for (TurNLPInstanceEntity nlpInstanceEntity : nlpInstanceEntities) {
			entityAttributes.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(),
					hmEntities.get(nlpInstanceEntity.getTurNLPEntity().getInternalName()));
		}

		logger.debug("getAttributes() entityAttributes: " + entityAttributes.toString());
		return entityAttributes;
	}

}
