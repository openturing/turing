/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.api.ml.model;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.ml.TurMLModel;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.ml.TurMLModelRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

@RestController
@RequestMapping("/api/ml/model")
@Tag(name ="Machine Learning Model", description = "Machine Learning Model API")
public class TurMLModelAPI {
	private static final Log logger = LogFactory.getLog(TurMLModelAPI.class);
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	TurMLModelRepository turMLModelRepository;

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
			logger.error(e);
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
				trainSB.append(vigTrainDocSentence.getTurMLCategory().getInternalName() + " ");
				trainSB.append(vigTrainDocSentence.getSentence().replaceAll("[\\t\\n\\r]", " ").trim());
				trainSB.append("\n");
			}
		}
		try (PrintWriter out = new PrintWriter("store/ml/train/generate.train")) {
			out.println(trainSB.toString().trim());
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		}

		String modelFile = "store/ml/model/generate.bin";
		try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelFile))) {
			InputStreamFactory isf = new InputStreamFactory() {
				public InputStream createInputStream() throws IOException {
					return new FileInputStream("store/ml/train/generate.train");
				}
			};

			Charset charset = StandardCharsets.UTF_8;
			ObjectStream<String> lineStream = new PlainTextByLineStream(isf, charset);
			ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

			DoccatFactory factory = new DoccatFactory();

			DoccatModel model = DocumentCategorizerME.train("en", sampleStream, TrainingParameters.defaultParams(),
					factory);
			model.serialize(modelOut);

		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

}
