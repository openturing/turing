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

package com.viglet.turing.api.nlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.pdfcleanup.PdfCleaner;
import com.itextpdf.pdfcleanup.autosweep.CompositeCleanupStrategy;
import com.itextpdf.pdfcleanup.autosweep.RegexBasedCleanupStrategy;
import com.viglet.turing.api.nlp.bean.TurNLPEntityValidateResponse;
import com.viglet.turing.api.nlp.bean.TurNLPValidateDocument;
import com.viglet.turing.api.nlp.bean.TurNLPValidateResponse;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.filesystem.commons.TurFileAttributes;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.nlp.TurNLPResponse;
import com.viglet.turing.nlp.output.blazon.RedactionCommand;
import com.viglet.turing.nlp.output.blazon.RedactionScript;
import com.viglet.turing.nlp.output.blazon.SearchString;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.utils.TurUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Pattern;
@Slf4j
@RestController
@RequestMapping("/api/nlp")
@Tag(name = "Natural Language Processing", description = "Natural Language Processing API")
public class TurNLPInstanceAPI {
    private static final String NLP_TEMP_FILE = "nlp_temp";
    private final TurNLPInstanceRepository turNLPInstanceRepository;
    private final TurNLPEntityRepository turNLPEntityRepository;
    private final TurNLPProcess turNLPProcess;

    @Inject
    public TurNLPInstanceAPI(TurNLPInstanceRepository turNLPInstanceRepository,
                             TurNLPEntityRepository turNLPEntityRepository, TurNLPProcess turNLPProcess) {
        this.turNLPInstanceRepository = turNLPInstanceRepository;
        this.turNLPEntityRepository = turNLPEntityRepository;
        this.turNLPProcess = turNLPProcess;
    }

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
            turNLPInstanceEdit.setEndpointURL(turNLPInstance.getEndpointURL());
            turNLPInstanceEdit.setKey(turNLPInstance.getKey());
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
        this.turNLPInstanceRepository.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Natural Language Processing")
    @PostMapping
    public TurNLPInstance turNLPInstanceAdd(@RequestBody TurNLPInstance turNLPInstance) {
        return turNLPInstance;

    }

    @PostMapping(value = "/{id}/validate/file/blazon", produces = MediaType.APPLICATION_XML_VALUE)
    public RedactionScript validateFile(@RequestParam("file") MultipartFile multipartFile, @PathVariable String id) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
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

                    File tempFile = File.createTempFile(NLP_TEMP_FILE + UUID.randomUUID(), null,
                            TurCommonsUtils.addSubDirToStoreDir("tmp"));
                    Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    try (FileInputStream fileInputStreamInner = new FileInputStream(tempFile)) {
                        parserInner.parse(fileInputStreamInner, handlerInner, metadataInner, parseContextInner);
                        contentFile.append(TurCommonsUtils.cleanTextContent(handlerInner.toString()));

                    } catch (IOException | SAXException | TikaException e) {
                        log.error(e.getMessage(), e);
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
                TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
                return createRedactionScript(turNLPResponse);
            }).orElse(new RedactionScript());

        } catch (IOException | SAXException | TikaException e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    private RedactionScript createRedactionScript(TurNLPResponse turNLPResponse) {
        List<RedactionCommand> redactionCommands = new ArrayList<>();
        RedactionScript redactionScript = new RedactionScript();
        redactionScript.setVersion("1");
        if (turNLPResponse != null) {
            turNLPResponse.getEntityMapWithProcessedValues().forEach((key, value) -> {
                if (value != null) {
                    value.forEach(term -> {
                        RedactionCommand redactionCommand = new RedactionCommand();
                        SearchString searchString = new SearchString();
                        searchString.setMatchWholeWord(true);
                        searchString.setString(String.format("%s", term));
                        redactionCommand.setSearchString(searchString);
                        redactionCommands.add(redactionCommand);
                    });
                }
            });
        }
        redactionScript.setRedactionCommands(redactionCommands);
        return redactionScript;
    }

    @PostMapping("/{id}/validate/document")
    public TurNLPValidateResponse validateDocument(@PathVariable String id,
                                                   @RequestParam("file") MultipartFile multipartFile,
                                                   @RequestParam("config") String turNLPValidateDocumentRequest) {

        File file = TurUtils.getFileFromMultipart(multipartFile);

        TurFileAttributes turFileAttributes = TurFileUtils.readFile(file);
        return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {

            TurNLPValidateDocument turNLPValidateDocument = null;
            try {
                turNLPValidateDocument = new ObjectMapper().readValue(turNLPValidateDocumentRequest,
                        TurNLPValidateDocument.class);

            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
            if (turFileAttributes != null && turNLPValidateDocument != null) {
                TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance,
                        turFileAttributes.getContent(), turNLPValidateDocument.getEntities());

                if (isPDF(file)) {
                    PdfReader pdfReader = null;
                    try {
                        pdfReader = new PdfReader(file);
                        pdfReader.setUnethicalReading(true);
                    } catch (IOException e) {
                        log.error(e.getMessage(), e);
                    }
                    if (pdfReader != null) {
                        try (PdfDocument pdf = new PdfDocument(pdfReader,
                                new PdfWriter(file.getAbsolutePath().concat("redact.pdf")))) {
                            CompositeCleanupStrategy strategy = new CompositeCleanupStrategy();
                            List<String> terms = getNLPTerms(turNLPResponse);
                            strategy.add(new RegexBasedCleanupStrategy(redactRegex(terms))
                                    .setRedactionColor(ColorConstants.DARK_GRAY));
                            try {
                                PdfCleaner.autoSweepCleanUp(pdf, strategy);
                            } catch (IOException e) {
                                log.error(e.getMessage(), e);
                            }
                        } catch (IOException e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
                return createNLPValidateResponse(turNLPInstance, turNLPResponse, turFileAttributes.getContent());
            } else {
                return null;
            }
        }).orElse(null);
    }

    private Pattern redactRegex(List<String> terms) {

        StringBuffer stringBuffer = new StringBuffer();
        terms.forEach(term -> stringBuffer.append(term.concat("|")));
        if (!stringBuffer.isEmpty()) {
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);
            String pattern = "\\b(".concat(stringBuffer.toString().replace(")", "").replace("(", "")).concat(")\\b");
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } else {
            return Pattern.compile("", Pattern.CASE_INSENSITIVE);
        }
    }

    @PostMapping("/{id}/validate/text/web")
    public TurNLPValidateResponse validateWeb(@PathVariable String id, @RequestBody TurNLPTextValidate textValidate) {
        return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
            TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
            return createNLPValidateResponse(turNLPInstance, turNLPResponse, textValidate.getText());
        }).orElse(null);
    }

    @PostMapping("/{id}/validate/text/blazon")
    public RedactionScript validateBlazon(@PathVariable String id, @RequestBody TurNLPTextValidate textValidate) {
        return this.turNLPInstanceRepository.findById(id).map(turNLPInstance -> {
            TurNLPResponse turNLPResponse = turNLPProcess.processTextByNLP(turNLPInstance, textValidate.getText());
            return createRedactionScript(turNLPResponse);
        }).orElse(null);
    }

    private TurNLPValidateResponse createNLPValidateResponse(TurNLPInstance turNLPInstance,
                                                             TurNLPResponse turNLPResponse, String text) {
        TurNLPValidateResponse turNLPValidateResponse = new TurNLPValidateResponse();
        turNLPValidateResponse.setVendor(turNLPInstance.getTurNLPVendor().getTitle());
        turNLPValidateResponse.setLocale(turNLPInstance.getLanguage());
        turNLPValidateResponse.setText(text);
        if (turNLPResponse != null && turNLPResponse.getEntityMapWithProcessedValues() != null) {
            for (Entry<String, List<String>> entityType : turNLPResponse.getEntityMapWithProcessedValues().entrySet()) {
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

    private List<String> getNLPTerms(TurNLPResponse turNLPResponse) {
        List<String> terms = new ArrayList<>();
        if (turNLPResponse != null && turNLPResponse.getEntityMapWithProcessedValues() != null) {
            for (Entry<String, List<String>> entityType : turNLPResponse.getEntityMapWithProcessedValues().entrySet()) {
                if (entityType.getValue() != null) {
                    terms.addAll(entityType.getValue());
                }
            }
        }
        return terms;
    }

    public boolean isPDF(File file) {
        try(Scanner input = new Scanner(new FileReader(file))) {
            while (input.hasNextLine()) {
                if (input.nextLine().contains("%PDF-")) {
                    return true;
                }
            }
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    @Getter
    public static class TurNLPTextValidate {
        String text;

        public TurNLPTextValidate() {
            super();
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}