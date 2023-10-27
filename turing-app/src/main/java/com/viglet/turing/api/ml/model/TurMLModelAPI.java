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

package com.viglet.turing.api.ml.model;

import com.viglet.turing.persistence.model.ml.TurMLModel;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.ml.TurMLModelRepository;
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
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/ml/model")
@Tag(name ="Machine Learning Model", description = "Machine Learning Model API")
public class TurMLModelAPI {
	@Autowired
	private TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	private TurMLModelRepository turMLModelRepository;

	@Operation(summary = "Machine Learning Model List")
	@GetMapping
	public List<TurMLModel> turMLModelList() {
		return this.turMLModelRepository.findAll();
	}

	@Operation(summary = "Show a Machine Learning Model")
	@GetMapping("/{id}")
	public TurMLModel turMLModelGet(@PathVariable int id) {
		return this.turMLModelRepository.findById(id).orElse(new TurMLModel());
	}

	@Operation(summary = "Update a Machine Learning Model")
	@PutMapping("/{id}")
	public TurMLModel turMLModelUpdate(@PathVariable int id, @RequestBody TurMLModel turMLModel) {
		return this.turMLModelRepository.findById(id).map(turMLModelEdit -> {
			turMLModelEdit.setInternalName(turMLModel.getInternalName());
			turMLModelEdit.setName(turMLModel.getName());
			turMLModelEdit.setDescription(turMLModel.getDescription());
			this.turMLModelRepository.save(turMLModelEdit);
			return turMLModelEdit;
		}).orElse(new TurMLModel());

	}

	@Transactional
	@Operation(summary = "Delete a Machine Learning Model")
	@DeleteMapping("/{id}")
	public boolean turMLModelDelete(@PathVariable int id) {
		this.turMLModelRepository.delete(id);
		return true;
	}

	@Operation(summary = "Create a Machine Learning Model")
	@PostMapping
	public TurMLModel turMLModelAdd(@RequestBody TurMLModel turMLModel) {
		this.turMLModelRepository.save(turMLModel);
		return turMLModel;

	}

	@GetMapping("/evaluation")
	public String turMLModelEvaluation() {
		File modelFile = new File("store/ml/model/generate.bin");
		InputStream modelStream;
		DoccatModel m = null;
		try {
			modelStream = new FileInputStream(modelFile);
			m = new DoccatModel(modelStream);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		String[] inputText = {
				"Republicans in Congress will start this week on an obstacle course even more arduous than health care: the first overhaul of the tax code in three decades." };
		DocumentCategorizerME myCategorizer = new DocumentCategorizerME(m);
		double[] outcomes = myCategorizer.categorize(inputText);
		String category = myCategorizer.getBestCategory(outcomes);

		JSONObject json = new JSONObject().put("text", inputText).put("category", category);

		return json.toString();
	}

	@GetMapping("/generate")
	public String turMLModelGenerate() {

		List<TurDataGroupSentence> turDataSentences = turDataGroupSentenceRepository.findAll();

		StringBuilder trainSB = new StringBuilder();

		for (TurDataGroupSentence vigTrainDocSentence : turDataSentences) {
			if (vigTrainDocSentence.getTurMLCategory() != null) {
				trainSB.append(vigTrainDocSentence.getTurMLCategory().getInternalName()).append(" ");
				trainSB.append(vigTrainDocSentence.getSentence().replaceAll("[\\t\\n\\r]", " ").trim());
				trainSB.append("\n");
			}
		}
		try (PrintWriter out = new PrintWriter("store/ml/train/generate.train")) {
			out.println(trainSB.toString().trim());
		} catch (FileNotFoundException e) {
			log.error(e.getMessage(), e);
		}

		String modelFile = "store/ml/model/generate.bin";
		try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
			InputStreamFactory isf = new InputStreamFactory() {
				public InputStream createInputStream() throws IOException {
					return new FileInputStream("store/ml/train/generate.train");
				}
			};

			ObjectStream<String> lineStream = new PlainTextByLineStream(isf, StandardCharsets.UTF_8);
			ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

			DoccatModel model = DocumentCategorizerME.train("en", sampleStream, TrainingParameters.defaultParams(),
					new DoccatFactory());
			model.serialize(modelOut);

		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

}
