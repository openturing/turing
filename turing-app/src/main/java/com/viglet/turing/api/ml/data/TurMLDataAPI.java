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
package com.viglet.turing.api.ml.data;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.xml.sax.SAXException;

import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;

import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.nlp.opennlp.TurOpenNLPConnector;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ml/data")
@Tag(name = "Machine Learning Data", description = "Machine Learning Data API")
public class TurMLDataAPI {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	@Autowired
	private TurDataRepository turDataRepository;
	@Autowired
	private TurNLPProcess turNLPProcess;
	@Autowired
	private TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	private TurOpenNLPConnector turOpenNLPConnector;
	
	@Operation(summary = "Machine Learning Data List")
	@GetMapping
	public List<TurData> turDataList() throws JSONException {
		return this.turDataRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Data")
	@GetMapping("/{id}")
	public TurData turDataGet(@PathVariable int id) throws JSONException {
		return this.turDataRepository.findById(id);
	}

	@Operation(summary = "Update a Machine Learning Data")
	@PutMapping("/{id}")
	public TurData turDataUpdate(@PathVariable int id, @RequestBody TurData turData) {
		TurData turDataEdit = this.turDataRepository.findById(id);
		turDataEdit.setName(turData.getName());
		turDataEdit.setType(turData.getType());
		this.turDataRepository.save(turDataEdit);
		return turDataEdit;
	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data")
	@DeleteMapping("/{id}")
	public boolean turDataDelete(@PathVariable int id) {
		this.turDataRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data")
	@PostMapping
	public TurData turDataAdd(@RequestBody TurData turData) {
		this.turDataRepository.save(turData);
		return turData;

	}

	@PostMapping("/import")
	@Transactional
	public String turDataImport(@RequestParam("file") MultipartFile multipartFile)
			 {

		BodyContentHandler handler = processMultifileAsPDF(multipartFile);

		String[] sentences = turOpenNLPConnector.sentenceDetect(turNLPProcess.getDefaultNLPInstance(), handler.toString());

		TurData turData = new TurData();

		turData.setName(multipartFile.getOriginalFilename());
		turData.setType(FilenameUtils.getExtension(multipartFile.getOriginalFilename()));
		this.turDataRepository.save(turData);

		for (String sentence : sentences) {
			TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
			turDataGroupSentence.setTurData(turData);
			turDataGroupSentence.setSentence(sentence);
			this.turDataGroupSentenceRepository.save(turDataGroupSentence);
		}

		JSONObject jsonTraining = new JSONObject();
		jsonTraining.put("sentences", sentences);
		return jsonTraining.toString();
	}

	private BodyContentHandler processMultifileAsPDF(MultipartFile multipartFile) {
		AutoDetectParser autoDetectParser = new AutoDetectParser();
		BodyContentHandler bodyContentHandler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();

		TesseractOCRConfig tesseractOCRConfig = new TesseractOCRConfig();
		PDFParserConfig pdfParserConfig = new PDFParserConfig();
		pdfParserConfig.setExtractInlineImages(true);

		ParseContext parseContext = new ParseContext();
		parseContext.set(TesseractOCRConfig.class, tesseractOCRConfig);
		parseContext.set(PDFParserConfig.class, pdfParserConfig);

		parseContext.set(Parser.class, autoDetectParser);

		try {
			autoDetectParser.parse(multipartFile.getInputStream(), bodyContentHandler, metadata, parseContext);
		} catch (IOException | SAXException | TikaException e) {
			logger.error(e.getMessage(), null, multipartFile, autoDetectParser, bodyContentHandler, metadata, tesseractOCRConfig, pdfParserConfig, parseContext, e);
		}
		return bodyContentHandler;
	}
}
