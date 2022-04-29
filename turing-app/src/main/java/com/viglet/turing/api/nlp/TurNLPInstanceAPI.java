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

package com.viglet.turing.api.nlp;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.nlp.output.blazon.RedactionCommand;
import com.viglet.turing.nlp.output.blazon.RedactionScript;
import com.viglet.turing.nlp.output.blazon.SearchString;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/nlp")
@Tag(name = "Natural Language Processing", description = "Natural Language Processing API")
public class TurNLPInstanceAPI {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	private static final String NLP_TEMP_FILE = "nlp_temp";
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	private TurNLPProcess turNLPProcess;

	@Operation(summary = "Natural Language Processing List")
	@GetMapping
	public List<TurNLPInstance> turNLPInstanceList() {
		return this.turNLPInstanceRepository.findAll();
	}

	@Operation(summary = "Show a Natural Language Processing")
	@GetMapping("/{id}")
	public TurNLPInstance turNLPInstanceGet(@PathVariable String id) {
		return this.turNLPInstanceRepository.findById(id).orElse(new TurNLPInstance());
	}

	@Operation(summary = "Natural Language Processing structure")
	@GetMapping("/structure")
	public TurNLPInstance turNLPInstanceStructure() {
		TurNLPInstance turNLPInstance = new TurNLPInstance();
		turNLPInstance.setTurNLPVendor(new TurNLPVendor());
		turNLPInstance.setLanguage("en_US");
		return turNLPInstance;

	}

	@Operation(summary = "Update a Natural Language Processing")
	@PutMapping("/{id}")
	public TurNLPInstance turNLPInstanceUpdate(@PathVariable String id, @RequestBody TurNLPInstance turNLPInstance) {
		return turNLPInstanceRepository.findById(id).map(turNLPInstanceEdit -> {
			turNLPInstanceEdit.setTitle(turNLPInstance.getTitle());
			turNLPInstanceEdit.setDescription(turNLPInstance.getDescription());
			turNLPInstanceEdit.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
			turNLPInstanceEdit.setHost(turNLPInstance.getHost());
			turNLPInstanceEdit.setPort(turNLPInstance.getPort());
			turNLPInstanceEdit.setEnabled(turNLPInstance.getEnabled());
			turNLPInstanceEdit.setLanguage(turNLPInstance.getLanguage());
			this.turNLPInstanceRepository.save(turNLPInstanceEdit);
			return turNLPInstanceEdit;
		}).orElse(new TurNLPInstance());

	}

	@Transactional
	@Operation(summary = "Delete a Natural Language Processing")
	@DeleteMapping("/{id}")
	public boolean turNLPInstanceDelete(@PathVariable String id) {
		this.turNLPInstanceRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Natural Language Processing")
	@PostMapping
	public TurNLPInstance turNLPInstanceAdd(@RequestBody TurNLPInstance turNLPInstance) {
		turNLPProcess.saveAndAssocEntity(turNLPInstance);
		return turNLPInstance;

	}

	@PostMapping(value = "/{id}/validate/file/blazon", produces = MediaType.APPLICATION_XML_VALUE)
	public RedactionScript validateFile(@RequestParam("file") MultipartFile multipartFile, @PathVariable String id) {
		try (InputStream inputStream = multipartFile.getInputStream()){
			StringBuilder contentFile = new StringBuilder();
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
						boolean outputHtml) throws IOException {

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
					
					File tempFile = File.createTempFile(NLP_TEMP_FILE + UUID.randomUUID(), null, TurCommonsUtils.addSubDirToStoreDir("tmp"));
					Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
						parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
						contentFile.append(TurCommonsUtils.cleanTextContent(handlerInner.toString()));

					} catch (IOException | SAXException | TikaException e) {
						logger.error(e);
					}
					tempFile.deleteOnExit();
				}
			};

			parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);

			parser.parse(inputStream, handler, metadata, parseContext);

			TurNLPTextValidate textValidate = new TurNLPTextValidate();
			contentFile.append(TurCommonsUtils.cleanTextContent(handler.toString()));
			textValidate.setText(contentFile.toString());

			return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
				Optional<TurNLP> turNLP = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
				return createRedactionScript(turNLP);
			}).orElse(new RedactionScript());

		} catch (IOException | SAXException | TikaException e) {
			logger.error(e);
		}

		return null;
	}

	private RedactionScript createRedactionScript(Optional<TurNLP> turNLP) {
		List<RedactionCommand> redactionCommands = new ArrayList<>();
		RedactionScript redationScript = new RedactionScript();
		redationScript.setVersion("1");
		turNLP.ifPresent(nlp -> nlp.getEntityMapWithProcessedValues().entrySet().forEach(entityType -> {
			if (entityType.getValue() != null) {
				entityType.getValue().forEach(term -> {
					RedactionCommand redactionCommand = new RedactionCommand();
					SearchString searchString = new SearchString();
					searchString.setMatchWholeWord(true);
					searchString.setString(String.format("%s", term));
					redactionCommand.setSearchString(searchString);
					redactionCommands.add(redactionCommand);
				});
			}
		}));

		redationScript.setRedactionCommands(redactionCommands);
		return redationScript;
	}

	@PostMapping("/{id}/validate/text/web")
	public TurNLPValidateResponse validateWeb(@PathVariable String id, @PathVariable String format,
			@RequestBody TurNLPTextValidate textValidate) {
		return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
			Optional<TurNLP> turNLP = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
			return createNLPValidateResponse(turNLPInstance, turNLP);
		}).orElse(null);
	}

	@PostMapping("/{id}/validate/text/blazon")
	public RedactionScript validateBlazon(@PathVariable String id, @PathVariable String format,
			@RequestBody TurNLPTextValidate textValidate) {
		return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
			Optional<TurNLP> turNLP = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
			return createRedactionScript(turNLP);
		}).orElse(null);
	}

	private TurNLPValidateResponse createNLPValidateResponse(TurNLPInstance turNLPInstance, Optional<TurNLP> turNLP) {
		TurNLPValidateResponse turNLPValidateResponse = new TurNLPValidateResponse();
		turNLPValidateResponse.setVendor(turNLPInstance.getTurNLPVendor().getTitle());
		turNLPValidateResponse.setLocale(turNLPInstance.getLanguage());
		if (turNLP.isPresent()) {
			for (Entry<String, List<String>> entityType : turNLP.get().getEntityMapWithProcessedValues().entrySet()) {
				if (entityType.getValue() != null) {
					TurNLPEntity turNLPEntity = turNLPEntityRepository.findByInternalName(entityType.getKey());
					TurNLPEntityValidateResponse turNLPEntityValidateResponse = new TurNLPEntityValidateResponse();

					turNLPEntityValidateResponse.setType(turNLPEntity);

					turNLPEntityValidateResponse.setTerms(entityType.getValue());
					turNLPValidateResponse.getEntities().add(turNLPEntityValidateResponse);
				}
			}
		}
		return turNLPValidateResponse;
	}

	public boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?"); // match a number with optional
												// '-' and decimal.
	}

	public static class TurNLPTextValidate {
		String text;

		public TurNLPTextValidate() {
			super();
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}