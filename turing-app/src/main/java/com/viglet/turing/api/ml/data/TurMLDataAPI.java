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

package com.viglet.turing.api.ml.data;

import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
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

import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;

import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.nlp.opennlp.TurOpenNLPConnector;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/data")
@Api(tags = "Machine Learning Data", description = "Machine Learning Data API")
public class TurMLDataAPI {
	
	@Autowired
	TurDataRepository turDataRepository;
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	TurOpenNLPConnector turOpenNLPConnector;
	
	@ApiOperation(value = "Machine Learning Data List")
	@GetMapping
	public List<TurData> turDataList() throws JSONException {
		return this.turDataRepository.findAll();
	}

	@ApiOperation(value = "Show a Machine Learning Data")
	@GetMapping("/{id}")
	public TurData turDataGet(@PathVariable int id) throws JSONException {
		return this.turDataRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning Data")
	@PutMapping("/{id}")
	public TurData turDataUpdate(@PathVariable int id, @RequestBody TurData turData) throws Exception {
		TurData turDataEdit = this.turDataRepository.findById(id);
		turDataEdit.setName(turData.getName());
		turDataEdit.setType(turData.getType());
		this.turDataRepository.save(turDataEdit);
		return turDataEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Data")
	@DeleteMapping("/{id}")
	public boolean turDataDelete(@PathVariable int id) throws Exception {
		this.turDataRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Data")
	@PostMapping
	public TurData turDataAdd(@RequestBody TurData turData) throws Exception {
		this.turDataRepository.save(turData);
		return turData;

	}

	@PostMapping("/import")	
	@Transactional
	public String turDataImport(@RequestParam("file") MultipartFile multipartFile)
			throws JSONException, IOException, SAXException, TikaException {

		AutoDetectParser parser = new AutoDetectParser();
		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();

		TesseractOCRConfig config = new TesseractOCRConfig();
		PDFParserConfig pdfConfig = new PDFParserConfig();
		pdfConfig.setExtractInlineImages(true);

		ParseContext parseContext = new ParseContext();
		parseContext.set(TesseractOCRConfig.class, config);
		parseContext.set(PDFParserConfig.class, pdfConfig);

		parseContext.set(Parser.class, parser);

		parser.parse(multipartFile.getInputStream(), handler, metadata, parseContext);
		

		String sentences[] = turOpenNLPConnector.sentenceDetect(handler.toString());

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
}
