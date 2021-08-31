/*
 * Copyright (C) 2016-2020 the original author or authors. 
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

package com.viglet.turing.api.nlp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.xml.bind.JAXB;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.nlp.output.blazon.RedactionCommand;
import com.viglet.turing.nlp.output.blazon.RedactionScript;
import com.viglet.turing.nlp.output.blazon.SearchString;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/nlp")
@Api(tags = "Natural Language Processing", description = "Natural Language Processing API")
public class TurNLPInstanceAPI {
	private static final Logger logger = LogManager.getLogger(TurNLPInstanceAPI.class);
	private static final String NLP_TEMP_FILE = "nlp_temp";
	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	TurNLP turNLP;

	@ApiOperation(value = "Natural Language Processing List")
	@GetMapping
	public List<TurNLPInstance> turNLPInstanceList() throws JSONException {
		return this.turNLPInstanceRepository.findAll();
	}

	@ApiOperation(value = "Show a Natural Language Processing")
	@GetMapping("/{id}")
	public TurNLPInstance turNLPInstanceGet(@PathVariable String id) throws JSONException {
		return this.turNLPInstanceRepository.findById(id).get();
	}

	@ApiOperation(value = "Natural Language Processing structure")
	@GetMapping("/structure")
	public TurNLPInstance turNLPInstanceStructure() {
		TurNLPInstance turNLPInstance = new TurNLPInstance();
		turNLPInstance.setTurNLPVendor(new TurNLPVendor());
		turNLPInstance.setLanguage("en_US");
		return turNLPInstance;

	}

	@ApiOperation(value = "Update a Natural Language Processing")
	@PutMapping("/{id}")
	public TurNLPInstance turNLPInstanceUpdate(@PathVariable String id, @RequestBody TurNLPInstance turNLPInstance)
			throws Exception {
		TurNLPInstance turNLPInstanceEdit = turNLPInstanceRepository.findById(id).get();
		turNLPInstanceEdit.setTitle(turNLPInstance.getTitle());
		turNLPInstanceEdit.setDescription(turNLPInstance.getDescription());
		turNLPInstanceEdit.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
		turNLPInstanceEdit.setHost(turNLPInstance.getHost());
		turNLPInstanceEdit.setPort(turNLPInstance.getPort());
		turNLPInstanceEdit.setEnabled(turNLPInstance.getEnabled());
		turNLPInstanceEdit.setLanguage(turNLPInstance.getLanguage());
		this.turNLPInstanceRepository.save(turNLPInstanceEdit);
		return turNLPInstanceEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Natural Language Processing")
	@DeleteMapping("/{id}")
	public boolean turNLPInstanceDelete(@PathVariable String id) throws Exception {
		this.turNLPInstanceRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Natural Language Processing")
	@PostMapping
	public TurNLPInstance turNLPInstanceAdd(@RequestBody TurNLPInstance turNLPInstance) throws Exception {
		this.turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
		return turNLPInstance;

	}

	@PostMapping(value = "/{id}/validate/file/blazon", produces = MediaType.APPLICATION_XML_VALUE)
	public RedactionScript validateFile(@RequestParam("file") MultipartFile multipartFile, @PathVariable String id) {

		InputStream inputStream;
		try {
			inputStream = multipartFile.getInputStream();
			StringBuffer contentFile = new StringBuffer();
			AutoDetectParser parser = new AutoDetectParser();
			// -1 = no limit of number of characters
			BodyContentHandler handler = new BodyContentHandler(-1);
			Metadata metadata = new Metadata();

			TesseractOCRConfig config = new TesseractOCRConfig();
			PDFParserConfig pdfConfig = new PDFParserConfig();
			pdfConfig.setExtractInlineImages(true);

			ParseContext parseContext = new ParseContext();
			parseContext.set(TesseractOCRConfig.class, config);
			parseContext.set(PDFParserConfig.class, pdfConfig);

			parseContext.set(Parser.class, parser);

			EmbeddedDocumentExtractor embeddedDocumentExtractor = new EmbeddedDocumentExtractor() {
				@Override
				public boolean shouldParseEmbedded(Metadata metadata) {
					return true;
				}

				@Override
				public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata,
						boolean outputHtml) throws SAXException, IOException {
					File tempFile = File.createTempFile(NLP_TEMP_FILE, null);

					BodyContentHandler handlerInner = new BodyContentHandler(-1);
					AutoDetectParser parserInner = new AutoDetectParser();

					Metadata metadataInner = new Metadata();

					TesseractOCRConfig tesseractConfigInner = new TesseractOCRConfig();
					PDFParserConfig pdfConfigInner = new PDFParserConfig();
					pdfConfigInner.setExtractInlineImages(true);

					ParseContext parseContextInner = new ParseContext();
					parseContextInner.set(TesseractOCRConfig.class, tesseractConfigInner);
					parseContextInner.set(PDFParserConfig.class, pdfConfigInner);

					parseContextInner.set(Parser.class, parserInner);
					Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
						parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
						contentFile.append(cleanTextContent(handlerInner.toString()));

					} catch (IOException | SAXException | TikaException e) {
						logger.error(e);
					}
					tempFile.deleteOnExit();
				}
			};

			parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);

			parser.parse(inputStream, handler, metadata, parseContext);

			TurNLPTextValidate textValidate = new TurNLPTextValidate();
			contentFile.append(cleanTextContent(handler.toString()));
			textValidate.setText(contentFile.toString());

			if (this.turNLPInstanceRepository.findById(id).isPresent()) {
				TurNLPInstance turNLPInstance = this.turNLPInstanceRepository.findById(id).get();
				turNLP.startup(turNLPInstance, textValidate.getText());

				RedactionScript redationScript = blazonEntity();

				return redationScript;

			}
		} catch (IOException | SAXException | TikaException e) {
			logger.error(e);
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private RedactionScript blazonEntity() {
		List<RedactionCommand> redactionCommands = new ArrayList<>();
		RedactionScript redationScript = new RedactionScript();

		redationScript.setVersion("1");
		for (Entry<String, Object> entityType : turNLP.validate().entrySet()) {
			if (entityType.getValue() != null) {
				TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName(entityType.getKey());
				for (String term : ((List<String>) entityType.getValue())) {
					RedactionCommand redactionCommand = new RedactionCommand();
					// redactionCommand.setComment(turNLPEntity.getName());
					SearchString searchString = new SearchString();
					searchString.setMatchWholeWord(true);
					searchString.setString(String.format("%s", term));
					redactionCommand.setSearchString(searchString);
					redactionCommands.add(redactionCommand);
				}
			}
		}

		redationScript.setRedactionCommands(redactionCommands);
		return redationScript;
	}

	@PostMapping("/{id}/validate/text/{format}")
	public String validate(@PathVariable String id, @PathVariable String format,
			@RequestBody TurNLPTextValidate textValidate) {
		return validateText(id, format, textValidate);
	}

	private static String cleanTextContent(String text) {
		if (logger.isDebugEnabled()) {
			logger.debug(
					String.format("Original Text: %s", text.replaceAll("\n", "\\\\n \n").replaceAll("\t", "\\\\t \t")));
		}
		// Remove 2 or more spaces
		text = text.trim().replaceAll("\\t|\\r", "\\n");
		text = text.trim().replaceAll(" +", " ");

		text = text.trim();

		if (logger.isDebugEnabled()) {
			logger.debug(String.format("Cleaned Text: %s", text));
		}
		return text;
	}

	@SuppressWarnings("unchecked")
	private String validateText(String id, String format, TurNLPTextValidate textValidate) {
		final String WEB_FORMAT = "web";
		final String BLAZON_FORMAT = "blazon";

		if (this.turNLPInstanceRepository.findById(id).isPresent()) {
			TurNLPInstance turNLPInstance = this.turNLPInstanceRepository.findById(id).get();
			turNLP.startup(turNLPInstance, textValidate.getText());
			if (format.equals(WEB_FORMAT)) {
				TurNLPValidateResponse turNLPValidateResponse = new TurNLPValidateResponse();
				turNLPValidateResponse.setVendor(turNLPInstance.getTurNLPVendor().getTitle());
				turNLPValidateResponse.setLocale(turNLPInstance.getLanguage());
				for (Entry<String, Object> entityType : turNLP.validate().entrySet()) {
					if (entityType.getValue() != null) {
						TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName(entityType.getKey());
						TurNLPEntityValidateResponse turNLPEntityValidateResponse = new TurNLPEntityValidateResponse();

						turNLPEntityValidateResponse.setType(turNLPEntity);

						turNLPEntityValidateResponse.setTerms((List<Object>) entityType.getValue());
						turNLPValidateResponse.getEntities().add(turNLPEntityValidateResponse);
					}
				}
				return turNLPValidateResponse.toString();
			} else if (format.equals(BLAZON_FORMAT)) {

				RedactionScript redationScript = blazonEntity();

				StringWriter sw = new StringWriter();
				JAXB.marshal(redationScript, sw);
				String xmlString = sw.toString();
				return xmlString;
			} else {

				return null;
			}

		} else {
			return null;
		}
	}

	public boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?"); // match a number with optional
												// '-' and decimal.
	}

	public static class TurNLPTextValidate {
		String text;

		public TurNLPTextValidate() {
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}