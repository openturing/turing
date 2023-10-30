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

import com.viglet.turing.persistence.model.ml.TurMLModel;
import com.viglet.turing.persistence.model.storage.TurDataGroupModel;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.ml.TurMLModelRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupModelRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.doccat.*;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/model")
@Tag(name = "Machine Learning Model by Group", description = "Machine Learning Model by Group API")
public class TurMLDataGroupModelAPI {
	@Autowired
	private TurDataGroupRepository turDataGroupRepository;
	@Autowired
	private TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	private TurMLModelRepository turMLModelRepository;
	@Autowired
	private TurDataGroupModelRepository turDataGroupModelRepository;

	@Operation(summary = "Machine Learning Data Group Model List")
	@GetMapping
	public List<TurDataGroupModel> turDataGroupModelList(@PathVariable int dataGroupId) {
		return turDataGroupRepository.findById(dataGroupId).map(this.turDataGroupModelRepository::findByTurDataGroup)
				.orElse(new ArrayList<>());

	}

	@Operation(summary = "Show a Machine Learning Data Group Model")
	@GetMapping("/{id}")
	public TurDataGroupModel turDataGroupModelGet(@PathVariable int dataGroupId, @PathVariable int id) {
		return this.turDataGroupModelRepository.findById(id);
	}

	@Operation(summary = "Update a Machine Learning Data Group Model")
	@PutMapping("/{id}")
	public TurDataGroupModel turDataGroupModelUpdate(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurDataGroupModel turDataGroupModel) {
		TurDataGroupModel turDataGroupModelEdit = this.turDataGroupModelRepository.findById(id);
		turDataGroupModelEdit.setTurMLModel(turDataGroupModel.getTurMLModel());
		this.turDataGroupModelRepository.save(turDataGroupModelEdit);
		return turDataGroupModelEdit;
	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Data Group Model")
	@DeleteMapping("/{id}")
	public boolean turDataGroupModelDelete(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupModelRepository.deleteById(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Data Group Model")
	@PostMapping
	public TurDataGroupModel turDataGroupModelAdd(@PathVariable int dataGroupId, @RequestBody TurMLModel turMLModel) {
		TurDataGroupModel turDataGroupModel = new TurDataGroupModel();
		return this.turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			turDataGroupModel.setTurMLModel(turMLModel);
			turDataGroupModel.setTurDataGroup(turDataGroup);
			this.turDataGroupModelRepository.save(turDataGroupModel);
			return turDataGroupModel;
		}).orElse(turDataGroupModel);

	}

	@GetMapping("/generate")
	public TurDataGroupModel turDataGroupModelGenerate(@PathVariable int dataGroupId) {

		return turDataGroupRepository.findById(dataGroupId).map(turDataGroup -> {
			List<TurDataGroupSentence> turDataSentences = this.turDataGroupSentenceRepository
					.findByTurDataGroup(turDataGroup);

			String modelFileName = Integer.toString(turDataGroup.getId());
			String trainFilePath = String.format("store/ml/train/%s.train", modelFileName);
			String modelFilePath = String.format("store/ml/model/%s.model", modelFileName);

			StringBuilder trainSB = new StringBuilder();

			for (TurDataGroupSentence vigTrainDocSentence : turDataSentences) {
				if (vigTrainDocSentence.getTurMLCategory() != null) {
					trainSB.append(vigTrainDocSentence.getTurMLCategory().getInternalName()).append(" ");
					trainSB.append(vigTrainDocSentence.getSentence().replaceAll("[\\t\\n\\r]", " ").trim());
					trainSB.append("\n");
				}
			}

			try (PrintWriter out = new PrintWriter(trainFilePath)) {
				out.println(trainSB.toString().trim());
			} catch (FileNotFoundException e) {
				log.error(e.getMessage(), e);
			}

			DoccatModel model = null;

			try {
				InputStreamFactory isf = new InputStreamFactory() {
					public InputStream createInputStream() throws IOException {
						return new FileInputStream(trainFilePath);
					}
				};

				Charset charset = StandardCharsets.UTF_8;
				ObjectStream<String> lineStream = new PlainTextByLineStream(isf, charset);
				ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

				DoccatFactory factory = new DoccatFactory();

				model = DocumentCategorizerME.train("en", sampleStream, TrainingParameters.defaultParams(), factory);

			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}

			if (model != null) {
				try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFilePath))) {
					model.serialize(modelOut);
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}

			}

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
			Date now = new Date();
			String strDate = sdf.format(now);

			TurMLModel turMLModel = new TurMLModel();
			turMLModel.setInternalName(Integer.toString(turDataGroup.getId()));
			turMLModel.setName(turDataGroup.getName());
			turMLModel.setDescription(strDate);
			turMLModelRepository.save(turMLModel);

			TurDataGroupModel turDataGroupModel = new TurDataGroupModel();
			turDataGroupModel.setTurDataGroup(turDataGroup);
			turDataGroupModel.setTurMLModel(turMLModel);
			turDataGroupModelRepository.save(turDataGroupModel);

			return turDataGroupModel;
		}).orElse(new TurDataGroupModel());

	}
}
