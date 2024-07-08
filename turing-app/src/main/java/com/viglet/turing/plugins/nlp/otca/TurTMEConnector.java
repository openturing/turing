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

package com.viglet.turing.plugins.nlp.otca;

import com.viglet.turing.commons.exception.TurException;
import com.viglet.turing.nlp.TurNLPEntityRequest;
import com.viglet.turing.nlp.TurNLPRequest;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.plugins.nlp.otca.response.xml.*;
import com.viglet.turing.plugins.nlp.otca.response.xml.ServerResponseEntityExtractorResultTermParentType.Parents;
import com.viglet.turing.solr.TurSolrField;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TurTMEConnector implements TurNLPPlugin {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final String COMPLEX_CONCEPTS = "ComplexConcepts";
	private static final String SIMPLE_CONCEPTS = "SimpleConcepts";
	private static final String LOG_KV = "{}: {}";

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
			byte[] data = request.trim().getBytes(StandardCharsets.UTF_8);
			int length = data.length;
			if (length == 0) {
				return null;
			}
			String[] endpoint = turNLPInstance.getEndpointURL().split(":");
			try (Socket socket = new Socket(endpoint[0], Integer.parseInt(endpoint[1]));
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
				int pos = 0;
				int size;
				while (length > 0) {
					size = input.read(data, pos, length);
					pos += size;
					length -= size;
					if (size == -1) {
						throw (new TurException("Invalid response"));
					}
				}
				return new String(data, StandardCharsets.UTF_8);
			}
		} catch (Exception e) {
			logger.debug("Server not reached: {}", e.getMessage());
			return null;
		}
	}

	@Override
	public Map<String, List<String>> processAttributesToEntityMap(TurNLPRequest turNLPRequest) {
		Map<String, List<String>> hmEntities = new HashMap<>();
		for (Object attrValue : turNLPRequest.getData().values()) {
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

			for (TurNLPEntityRequest entity : turNLPRequest.getEntities()) {
				sb.append("<Cartridge>").append(entity.getName()).append("</Cartridge>");
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
			sb.append("<Cartridge>CONSTRUCAO CIVIL</Cartridge>");
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
			sb.append("<Cartridge>COMÃƒâ€°RCIO VAREJISTA E ATACADISTA</Cartridge>");
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
			sb.append(TurSolrField.convertFieldToString(attrValue));
			sb.append("</NSTEIN_Text>");
			sb.append("</Text>");
			sb.append("</Texts>");
			sb.append("</Nserver>");

			this.toJSON(this.request(turNLPRequest.getTurNLPInstance(), sb.toString()), hmEntities);
		}

		return this.getAttributes(turNLPRequest, hmEntities);

	}

	public void toJSON(String xml, Map<String, List<String>> hmEntities) {

		InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

		JAXBContext jaxbContext;
		try {
			logger.debug(xml);
			jaxbContext = JAXBContext.newInstance(com.viglet.turing.plugins.nlp.otca.response.xml.ObjectFactory.class);
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			spf.setFeature("http://xml.org/sax/features/validation", false);
			spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			XMLReader xmlReader = spf.newSAXParser().getXMLReader();
			SAXSource source = new SAXSource(xmlReader, new InputSource(is));

			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

			ServerResponseType serverResponseType = unmarshaller.unmarshal(source, ServerResponseType.class).getValue();

			logger.debug("getErrorDescription: {} ", serverResponseType.getErrorDescription());
			logger.debug("getVersion: {}", serverResponseType.getVersion());
			for (Object result : serverResponseType.getResults().getPingOrGetSupportedEncodingsOrLanguagedetector()) {
				this.getResult(result, hmEntities);
			}
			for (Object result : serverResponseType.getResults()
					.getLanguageDetectorOrNConceptExtractorOrNCategorizer()) {
				this.getResult(result, hmEntities);
			}

		} catch (JAXBException | SAXException | ParserConfigurationException e) {
			logger.error(e);
		}
	}

	public void getParentTerms(Parents parents, Map<String, List<String>> hmEntities, String cartridgeID) {
		if (parents != null) {
			for (ServerResponseEntityExtractorResultTermParentType parent : parents.getParent()) {
				hmEntities.get(cartridgeID).add(parent.getTerm());
				this.getParentTerms(parent.getParents(), hmEntities, cartridgeID);
			}
		}

	}

	public void getResult(Object result, Map<String, List<String>> hmEntities) {

		logger.debug("iterateResults... {}", result);

		if (result instanceof ServerResponseConceptExtractorResultType concepts) {
			logger.debug("Concepts: {}", concepts.getName());
			setComplexConcepts(hmEntities, concepts);
			setSimpleConcepts(hmEntities, concepts);
		} else if (result instanceof ServerResponseEntityExtractorResultType serverResponseEntityExtractorResultType) {
			setEntities(serverResponseEntityExtractorResultType, hmEntities);
		} else if (result instanceof ServerResponseCategorizerResultType serverResponseCategorizerResultType) {
			setCategorizer(serverResponseCategorizerResultType, hmEntities);

		}

	}

	private void setEntities(ServerResponseEntityExtractorResultType entities, Map<String, List<String>> hmEntities) {
		for (Object nf : entities.getNfExtractOrNfFullTextSearch()) {
			if (nf instanceof ServerResponseEntityExtractorResultExtractResultType extractor) {
				setTerms(hmEntities, extractor);
			} else if (nf instanceof ServerResponseEntityExtractorResultFullTextSearchResultType fullText) {
				setFullText(hmEntities, fullText);
			}

		}
	}

	private void setTerms(Map<String, List<String>> hmEntities,
			ServerResponseEntityExtractorResultExtractResultType extractor) {
		for (ServerResponseEntityExtractorResultTermType term : extractor.getExtractedTerm()) {
			logger.debug("getCartridgeID: {}", term.getCartridgeID());
			if (!hmEntities.containsKey(term.getCartridgeID())) {
				hmEntities.put(term.getCartridgeID(), new ArrayList<>());
			}

			logger.debug("getId: {}", term.getId());
			logger.debug("getNfinderNormalized: {}", term.getNfinderNormalized());
			if (term.getSubterms() != null) {
				for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms().getSubterm()) {
					hmEntities.get(term.getCartridgeID()).add(subterm.getValue());
				}

			}
		}
	}

	private void setFullText(Map<String, List<String>> hmEntities,
			ServerResponseEntityExtractorResultFullTextSearchResultType fullText) {
		for (ServerResponseEntityExtractorResultTermType term : fullText.getExtractedTerm()) {
			if (!hmEntities.containsKey(term.getCartridgeID())) {
				hmEntities.put(term.getCartridgeID(), new ArrayList<>());
			}
			if (term.getSubterms() != null) {
				for (ServerResponseEntityExtractorResultTermOccurenceType subterm : term.getSubterms().getSubterm()) {
					hmEntities.get(term.getCartridgeID()).add(subterm.getValue());
				}

			}
			if (term.getHierarchy() != null && term.getHierarchy().getBase() != null
					&& term.getHierarchy().getBase().getParents() != null) {

				for (ServerResponseEntityExtractorResultTermParentType parent : term.getHierarchy().getBase()
						.getParents().getParent()) {
					hmEntities.get(term.getCartridgeID()).add(parent.getTerm());
					getParentTerms(parent.getParents(), hmEntities, term.getCartridgeID());
				}
			}
		}
	}

	private void setCategorizer(ServerResponseCategorizerResultType categorizer, Map<String, List<String>> hmEntities) {
		setCategory(categorizer);
		setKnowledgeBase(hmEntities, categorizer);
	}

	private void setKnowledgeBase(Map<String, List<String>> hmEntities,
			ServerResponseCategorizerResultType categorizer) {
		if (categorizer.getKnowledgeBase() != null) {
			for (ServerResponseCategorizerResultKnowledgeBaseType kb : categorizer.getKnowledgeBase()) {
				if (!hmEntities.containsKey(kb.getKBid())) {
					hmEntities.put(kb.getKBid(), new ArrayList<>());
				}
				kbCategories(hmEntities, kb);
				kbRejectedCategories(kb);
			}

		}
	}

	private void kbCategories(Map<String, List<String>> hmEntities,
			ServerResponseCategorizerResultKnowledgeBaseType kb) {
		if (kb.getCategories() != null) {
			for (ServerResponseCategorizerResultCategoryType category : kb.getCategories().getCategory()) {

				for (Serializable content : category.getContent()) {
					logger.debug("KB Content: {}", content);
					hmEntities.get(kb.getKBid()).add(content.toString().replaceAll(category.getId() + " - ", ""));
				}
			}
		}
	}

	private void kbRejectedCategories(ServerResponseCategorizerResultKnowledgeBaseType kb) {
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

	private void setCategory(ServerResponseCategorizerResultType categorizer) {
		if (categorizer.getCategories() != null) {
			for (ServerResponseCategorizerResultCategoryType category : categorizer.getCategories().getCategory()) {
				for (Serializable content : category.getContent()) {
					logger.debug("Category Content: {}", content);
				}
				logger.debug("Category ID: {}", category.getId());
				logger.debug("Category Weight: {}", category.getWeight());
			}
		}
	}

	private void setSimpleConcepts(Map<String, List<String>> hmEntities,
			ServerResponseConceptExtractorResultType concepts) {
		if (concepts.getSimpleConcepts() != null) {
			logger.debug(SIMPLE_CONCEPTS);
			hmEntities.computeIfAbsent(SIMPLE_CONCEPTS, k -> hmEntities.put(k, new ArrayList<>()));
			for (Object simpleConcepts : concepts.getSimpleConcepts().getConceptOrExtractedTerm()) {
				if (simpleConcepts instanceof ServerResponseConceptExtractorResultConcept1Type serverResponseConceptExtractorResultConcept1Type) {
					hmEntities.get(SIMPLE_CONCEPTS).add(serverResponseConceptExtractorResultConcept1Type.getValue());
				}
				if (simpleConcepts instanceof ServerResponseConceptExtractorResultConcept2Type serverResponseConceptExtractorResultConcept2Type) {
					hmEntities.get(SIMPLE_CONCEPTS)
							.add(serverResponseConceptExtractorResultConcept2Type.getContent().toString());
					logger.debug(LOG_KV, SIMPLE_CONCEPTS,
							serverResponseConceptExtractorResultConcept2Type.getContent());
				}
			}
		}
	}

	private void setComplexConcepts(Map<String, List<String>> hmEntities,
			ServerResponseConceptExtractorResultType concepts) {
		if (concepts.getComplexConcepts() != null) {
			logger.debug(COMPLEX_CONCEPTS);
			hmEntities.computeIfAbsent(COMPLEX_CONCEPTS, k -> hmEntities.put(k, new ArrayList<>()));
			for (Object complexConcept : concepts.getComplexConcepts().getConceptOrExtractedTerm()) {
				if (complexConcept instanceof ServerResponseConceptExtractorResultConcept1Type serverResponseConceptExtractorResultConcept1Type) {
					hmEntities.get(COMPLEX_CONCEPTS).add(serverResponseConceptExtractorResultConcept1Type.getValue());
					logger.debug(LOG_KV, COMPLEX_CONCEPTS, serverResponseConceptExtractorResultConcept1Type.getValue());
				}
				if (complexConcept instanceof ServerResponseConceptExtractorResultConcept2Type serverResponseConceptExtractorResultConcept2Type) {
					hmEntities.get(COMPLEX_CONCEPTS)
							.add(serverResponseConceptExtractorResultConcept2Type.getContent().toString());
					logger.debug(LOG_KV, COMPLEX_CONCEPTS,
							serverResponseConceptExtractorResultConcept2Type.getContent());
				}
			}
		}
	}

	public Map<String, List<String>> getAttributes(TurNLPRequest turNLPRequest, Map<String, List<String>> hmEntities) {

		logger.debug("getAttributes() hmEntities: {}", hmEntities);
		logger.debug("getAttributes() nlpInstanceEntities: {}", turNLPRequest.getEntities());
		Map<String, List<String>> entityAttributes = new HashMap<>();
		for (TurNLPEntityRequest turNLPEntityRequest : turNLPRequest.getEntities()) {
			entityAttributes.put(turNLPEntityRequest.getTurNLPVendorEntity().getTurNLPEntity().getInternalName(),
					hmEntities.get(turNLPEntityRequest.getName()));
		}

		logger.debug("getAttributes() entityAttributes: {}", entityAttributes);
		return entityAttributes;
	}

}
