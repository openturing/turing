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

package com.viglet.turing.plugins.nlp.otca;

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
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseCategorizerResultCategoryType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseCategorizerResultKnowledgeBaseType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseCategorizerResultType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseConceptExtractorResultConcept1Type;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseConceptExtractorResultConcept2Type;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseConceptExtractorResultType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultExtractResultType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultFullTextSearchResultType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultTermOccurenceType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultTermParentType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultTermParentType.Parents;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultTermType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultType;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseType;
import com.viglet.turing.solr.TurSolrField;

@Component
public class TurTMEConnector implements TurNLPImpl {
	private static final Logger logger = LogManager.getLogger(TurTMEConnector.class);
	@Autowired
	private TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;
	@Autowired
	private TurSolrField turSolrField;

	private List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	private Map<String, List<Object>> hmEntities = new HashMap<>();
	private TurNLPInstance turNLPInstance = null;
	private JSONObject json;
	private static final int PRETTY_PRINT_INDENT_FACTOR = 4;

	public void startup(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;

		nlpInstanceEntities = turNLPInstanceEntityRepository.findByTurNLPInstanceAndEnabled(turNLPInstance, 1);
	}

	/**
	 * Send XML request to TME
	 * 
	 * @param request XML request
	 * @return XML response
	 */
	public String request(TurNLPInstance turNLPInstance, String request) {
		try {
			if (request == null) {
				return null;
			}
			byte[] data = request.trim().getBytes( StandardCharsets.UTF_8);
			int length = data.length;
			if (length == 0) {
				return null;
			}
			try (Socket socket = new Socket(turNLPInstance.getHost(), turNLPInstance.getPort());
					OutputStream output = socket.getOutputStream();
					InputStream input = socket.getInputStream()) {
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
				return new String(data,StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			logger.debug("Server not reached: {}" , e.getMessage());
			return null;
		}
	}

	public Map<String, Object> retrieve(Map<String, Object> attributes) {

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
			sb.append(" <Cartridge>CONFECÃƒâ€¡ÃƒÆ’O</Cartridge>");
			sb.append("<Cartridge>ARTESANATO</Cartridge>");
			sb.append(" <Cartridge>AQUICULTURA</Cartridge>");
			sb.append("<Cartridge>COURO E CALÃƒâ€¡ADOS</Cartridge>");
			sb.append("<Cartridge>CONSTRUÃƒâ€¡ÃƒÆ’O CIVIL</Cartridge>");
			sb.append("<Cartridge>ALIMENTO</Cartridge>");
			sb.append("<Cartridge>ROCHA ORNAMENTAL</Cartridge>");
			sb.append("<Cartridge>AGRICULTURA</Cartridge>");
			sb.append("<Cartridge>GRÃƒï¿½FICA E EDITORA</Cartridge>");
			sb.append("<Cartridge>TURISMO</Cartridge>");
			sb.append("<Cartridge>TÃƒÅ XTIL</Cartridge>");
			sb.append("<Cartridge>CERÃƒâ€šMICA</Cartridge>");
			sb.append("<Cartridge>BEBIDA</Cartridge>");
			sb.append("<Cartridge>CULTURA, ENTRETENIMENTO E LAZER</Cartridge>");
			sb.append("<Cartridge>RECICLAGEM</Cartridge>");
			sb.append("<Cartridge>PLÃƒï¿½STICO E BORRACHA</Cartridge>");
			sb.append("<Cartridge>METALURGIA, METAL MECÃƒâ€šNICA E AUTO PEÃƒâ€¡AS</Cartridge>");
			sb.append("<Cartridge>HIGIENE PESSOAL, PERFUMARIA E COSMÃƒâ€°TICO</Cartridge>");
			sb.append("<Cartridge>ELETRO-ELETRÃƒâ€�NICA</Cartridge>");
			sb.append("<Cartridge>PETRÃƒâ€œLEO, GÃƒï¿½S E ENERGIA</Cartridge>");
			sb.append("<Cartridge>QUÃƒï¿½MICA</Cartridge>");
			sb.append("<Cartridge>GEMA, JÃƒâ€œIA E BIJUTERIA</Cartridge>");
			sb.append("<Cartridge>EMPREENDEDORISMO</Cartridge>");
			sb.append("<Cartridge>GESTÃƒÆ’O EMPRESARIAL</Cartridge>");
			sb.append("<Cartridge>AMBIENTE ECONÃƒâ€�MICO E SOCIAL</Cartridge>");
			sb.append("<Cartridge>COMÃƒâ€°RCIO VAREJISTA E ATACADISTA</Cartridge>");
			sb.append("<Cartridge>SAÃƒÅ¡DE, BELEZA E MEDICINA</Cartridge>");
			sb.append("<Cartridge>BIOTECNOLOGIA</Cartridge>");
			sb.append("<Cartridge>PECUÃƒï¿½RIA</Cartridge>");
			sb.append("<Cartridge>MADEIRA E MOBILIÃƒï¿½RIO</Cartridge>");
			sb.append("<Cartridge>AMBIENTE NORMATIVO</Cartridge>");
			sb.append("<Cartridge>TECNOLOGIA DA INFORMAÃƒâ€¡ÃƒÆ’O E COMUNICAÃƒâ€¡ÃƒÆ’O</Cartridge>");
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
			jaxbContext = JAXBContext.newInstance(com.viglet.turing.plugins.nlp.otca.response.xml.ObjectFactory.class);
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			spf.setFeature("http://xml.org/sax/features/validation", false);

			XMLReader xmlReader = spf.newSAXParser().getXMLReader();
			SAXSource source = new SAXSource(xmlReader, new InputSource(is));

			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

			ServerResponseType serverResponseType = unmarshaller.unmarshal(source, ServerResponseType.class).getValue();

			logger.debug("getErrorDescription: " + serverResponseType.getErrorDescription());
			logger.debug("getVersion: " + serverResponseType.getVersion());
			for (Object result : serverResponseType.getResults().getPingOrGetSupportedEncodingsOrLanguagedetector()) {
				this.getResult(result);
			}
			for (Object result : serverResponseType.getResults()
					.getLanguageDetectorOrNConceptExtractorOrNCategorizer()) {
				this.getResult(result);
			}

		} catch (JAXBException | SAXException | ParserConfigurationException e) {
			logger.error(e);
		}
		try {
			this.json = XML.toJSONObject(xml);

			String jsonPrettyPrintString = json.toString(PRETTY_PRINT_INDENT_FACTOR);
			jsonResult = jsonPrettyPrintString;
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

		logger.debug("iterateResults... {}", result.toString());

		if (result instanceof ServerResponseConceptExtractorResultType) {
			ServerResponseConceptExtractorResultType concepts = (ServerResponseConceptExtractorResultType) result;
			logger.debug("Concepts: {}", concepts.getName());
			if (concepts.getComplexConcepts() != null) {
				logger.debug("ComplexConcept");
				if (!hmEntities.containsKey("ComplexConcepts")) {
					hmEntities.put("ComplexConcepts", new ArrayList<>());
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
						logger.debug("getCartridgeID: {}", term.getCartridgeID());
						if (!hmEntities.containsKey(term.getCartridgeID())) {
							hmEntities.put(term.getCartridgeID(), new ArrayList<>());
						}

						logger.debug("getId: " + term.getId());
						logger.debug("getNfinderNormalized: " + term.getNfinderNormalized());
						if (term.getSubterms() != null) {
							for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms()
									.getSubterm()) {
								hmEntities.get(term.getCartridgeID()).add(subterm.getValue());
							}

						}
					}
				} else if (nf instanceof ServerResponseEntityExtractorResultFullTextSearchResultType) {
					ServerResponseEntityExtractorResultFullTextSearchResultType fullText = (ServerResponseEntityExtractorResultFullTextSearchResultType) nf;
					for (ServerResponseEntityExtractorResultTermType term : fullText.getExtractedTerm()) {
						if (!hmEntities.containsKey(term.getCartridgeID())) {
							hmEntities.put(term.getCartridgeID(), new ArrayList<>());
						}
						if (term.getSubterms() != null) {
							for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms()
									.getSubterm()) {
								hmEntities.get(term.getCartridgeID()).add(subterm.getValue());
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
						logger.debug("Category Content: {}", content.toString());
					}
					logger.debug("Category ID: {}", category.getId());
					logger.debug("Category Weight: {}", category.getWeight());
				}
			}
			if (categorizer.getKnowledgeBase() != null) {
				for (ServerResponseCategorizerResultKnowledgeBaseType kb : categorizer.getKnowledgeBase()) {
					if (!hmEntities.containsKey(kb.getKBid())) {
						hmEntities.put(kb.getKBid(), new ArrayList<>());
					}
					if (kb.getCategories() != null) {
						for (ServerResponseCategorizerResultCategoryType category : kb.getCategories().getCategory()) {

							for (Serializable content : category.getContent()) {
								logger.debug("KB Content: {}", content);
								hmEntities.get(kb.getKBid())
										.add(content.toString().replaceAll(category.getId() + " - ", ""));
							}
						}
					}
					if (kb.getRejectedCategories() != null) {
						for (ServerResponseCategorizerResultCategoryType category : kb.getRejectedCategories()
								.getRejectedCategory()) {
							for (Serializable content : category.getContent()) {
								logger.debug("KB Content Rejected: {}", content);
							}
							logger.debug("KB ID Rejected: {}", category.getId());
							logger.debug("KB Weight Rejected: {}", category.getWeight());

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
