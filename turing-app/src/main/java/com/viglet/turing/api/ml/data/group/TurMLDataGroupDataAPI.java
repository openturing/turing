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

package com.viglet.turing.api.ml.data.group;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;

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
import com.viglet.turing.persistence.model.storage.TurDataGroupData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupDataRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.opennlp.TurOpenNLPConnector;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/data")
@Api(tags = "Machine Learning Data by Group", description = "Machine Learning Data by Group API")
public class TurMLDataGroupDataAPI {
	private static final Log logger = LogFactory.getLog(TurMLDataGroupDataAPI.class);
	@Autowired
	TurDataGroupRepository turDataGroupRepository;
	@Autowired
	TurDataGroupDataRepository turDataGroupDataRepository;
	@Autowired
	TurDataRepository turDataRepository;
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	TurOpenNLPConnector turOpenNLPConnector;

	@ApiOperation(value = "Machine Learning Data Group Data List")
	@GetMapping
	public List<TurDataGroupData> turDataGroupDataList(@PathVariable int dataGroupId) {
		return this.turDataGroupRepository.findById(dataGroupId)
				.map(this.turDataGroupDataRepository::findByTurDataGroup).orElse(new ArrayList<>());
	}

	@ApiOperation(value = "Show a Machine Learning Data Group Data")
	@GetMapping("/{id}")
	public TurDataGroupData turDataGroupDataGet(@PathVariable int dataGroupId, @PathVariable int id) {
		return this.turDataGroupDataRepository.findById(id).orElse(new TurDataGroupData());
	}

	@ApiOperation(value = "Update a Machine Learning Data Group Data")
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
	@ApiOperation(value = "Delete a Machine Learning Data Group Data")
	@DeleteMapping("/{id}")
	public boolean turDataGroupDataDelete(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupDataRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Data Group Data")
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
				logger.error(e);
			}

			String sentences[] = turOpenNLPConnector.sentenceDetect(handler.toString());

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
