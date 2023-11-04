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
package com.viglet.turing.api.ml.data.group;

import com.google.inject.Inject;
import com.viglet.turing.nlp.TurNLPProcess;
import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroupData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupDataRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.nlp.opennlp.TurOpenNLPConnector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/data")
@Tag(name ="Machine Learning Data by Group", description = "Machine Learning Data by Group API")
public class TurMLDataGroupDataAPI {
	private final TurDataGroupRepository turDataGroupRepository;
	private final TurDataGroupDataRepository turDataGroupDataRepository;
	private final TurDataRepository turDataRepository;
	private final TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	private final TurOpenNLPConnector turOpenNLPConnector;
	private final TurNLPProcess turNLPProcess;

	@Inject
	public TurMLDataGroupDataAPI(TurDataGroupRepository turDataGroupRepository,
								 TurDataGroupDataRepository turDataGroupDataRepository,
								 TurDataRepository turDataRepository,
								 TurDataGroupSentenceRepository turDataGroupSentenceRepository,
								 TurOpenNLPConnector turOpenNLPConnector,
								 TurNLPProcess turNLPProcess) {
		this.turDataGroupRepository = turDataGroupRepository;
		this.turDataGroupDataRepository = turDataGroupDataRepository;
		this.turDataRepository = turDataRepository;
		this.turDataGroupSentenceRepository = turDataGroupSentenceRepository;
		this.turOpenNLPConnector = turOpenNLPConnector;
		this.turNLPProcess = turNLPProcess;
	}

	@Operation(summary = "Machine Learning Data Group Data List")
	@GetMapping
	public List<TurDataGroupData> turDataGroupDataList(@PathVariable int dataGroupId) {
		return this.turDataGroupRepository.findById(dataGroupId)
				.map(this.turDataGroupDataRepository::findByTurDataGroup).orElse(new ArrayList<>());
	}

	@Operation(summary = "Show a Machine Learning Data Group Data")
	@GetMapping("/{id}")
	public TurDataGroupData turDataGroupDataGet(@PathVariable int dataGroupId, @PathVariable int id) {
		return this.turDataGroupDataRepository.findById(id).orElse(new TurDataGroupData());
	}

	@Operation(summary = "Update a Machine Learning Data Group Data")
	@PutMapping("/{id}")
	public TurDataGroupData turDataGroupDataUpdate(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurData turMLData) {
		return this.turDataGroupDataRepository.findById(id).map(turDataGroupDataEdit -> {
			turDataGroupDataEdit.setTurData(turMLData);
			this.turDataGroupDataRepository.save(turDataGroupDataEdit);
			return turDataGroupDataEdit;
		}).orElse(new TurDataGroupData());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Group Data")
	@DeleteMapping("/{id}")
	public boolean turDataGroupDataDelete(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupDataRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Group Data")
	@PostMapping
	public TurDataGroupData turDataGroupDataAdd(@PathVariable int dataGroupId,
			@RequestBody TurDataGroupData turDataGroupData) {
		return this.turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			turDataGroupData.setTurDataGroup(turDataGroup);
			this.turDataGroupDataRepository.save(turDataGroupData);
			return turDataGroupData;
		}).orElse(new TurDataGroupData());

	}

	@PostMapping("/import")
	@Transactional
	public TurDataGroupData turDataGroupDataImport(@PathVariable int dataGroupId,
			@RequestParam("file") MultipartFile multipartFile) {

		return this.turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			BodyContentHandler handler = new BodyContentHandler(-1);
			Metadata metadata = new Metadata();

			ParseContext pcontext = new ParseContext();

			// parsing the document using PDF parser
			PDFParser pdfparser = new PDFParser();
			try {
				pdfparser.parse(multipartFile.getInputStream(), handler, metadata, pcontext);
			} catch (IOException | SAXException | TikaException e) {
				log.error(e.getMessage(), e);
			}
			String[] sentences = turOpenNLPConnector.sentenceDetect(turNLPProcess.getDefaultNLPInstance(), handler.toString());

			TurData turData = new TurData();

			turData.setName(multipartFile.getOriginalFilename());
			turData.setType(FilenameUtils.getExtension(multipartFile.getOriginalFilename()));
			this.turDataRepository.save(turData);

			for (String sentence : sentences) {
				TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
				turDataGroupSentence.setTurData(turData);
				turDataGroupSentence.setSentence(sentence);
				turDataGroupSentence.setTurDataGroup(turDataGroup);
				turDataGroupSentenceRepository.save(turDataGroupSentence);
			}

			TurDataGroupData turDataGroupData = new TurDataGroupData();

			turDataGroupData.setTurData(turData);
			turDataGroupData.setTurDataGroup(turDataGroup);
			turDataGroupDataRepository.save(turDataGroupData);

			return turDataGroupData;
		}).orElse(new TurDataGroupData());

	}
}
