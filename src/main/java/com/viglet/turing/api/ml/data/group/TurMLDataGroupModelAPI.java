package com.viglet.turing.api.ml.data.group;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.json.JSONException;
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
import org.xml.sax.SAXException;

import com.viglet.turing.persistence.model.ml.TurMLModel;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupModel;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;
import com.viglet.turing.persistence.repository.ml.TurMLModelRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupModelRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

import io.swagger.annotations.ApiOperation;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

@RestController
@RequestMapping("/api/ml/data/group/{dataGroupId}/model")
public class TurMLDataGroupModelAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;
	@Autowired
	TurMLCategoryRepository turMLCategoryRepository;
	@Autowired
	TurMLModelRepository turMLModelRepository;
	@Autowired
	TurDataGroupModelRepository turDataGroupModelRepository;

	@ApiOperation(value = "Machine Learning Data Group Model List")
	@GetMapping
	public List<TurDataGroupModel> list(@PathVariable int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupRepository.findById(dataGroupId);
		return this.turDataGroupModelRepository.findByTurDataGroup(turDataGroup);
	}

	@ApiOperation(value = "Show a Machine Learning Data Group Model")
	@GetMapping("/{id}")
	public TurDataGroupModel mlSolution(@PathVariable int dataGroupId,
			@PathVariable int id) throws JSONException {
		return this.turDataGroupModelRepository.findById(id);
	}

	@ApiOperation(value = "Update a Machine Learning Data Group Model")
	@PutMapping("/{id}")
	public TurDataGroupModel update(@PathVariable int dataGroupId, @PathVariable int id,
			@RequestBody TurDataGroupModel turDataGroupModel) throws Exception {
		TurDataGroupModel turDataGroupModelEdit = this.turDataGroupModelRepository.findById(id);
		turDataGroupModelEdit.setTurMLModel(turDataGroupModel.getTurMLModel());
		this.turDataGroupModelRepository.save(turDataGroupModelEdit);
		return turDataGroupModelEdit;
	}

	@Transactional
	@ApiOperation(value = "Delete a Machine Learning Data Group Model")
	@DeleteMapping("/{id}")
	public boolean deleteEntity(@PathVariable int dataGroupId, @PathVariable int id) {
		this.turDataGroupModelRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Create a Machine Learning Data Group Model")
	@PostMapping
	public TurDataGroupModel add(@PathVariable int dataGroupId, @RequestBody TurMLModel turMLModel) throws Exception {
		TurDataGroupModel turDataGroupModel = new TurDataGroupModel();
		TurDataGroup turDataGroup = this.turDataGroupRepository.findById(dataGroupId);
		turDataGroupModel.setTurMLModel(turMLModel);
		turDataGroupModel.setTurDataGroup(turDataGroup);
		this.turDataGroupModelRepository.save(turDataGroupModel);
		return turDataGroupModel;

	}

	@GetMapping("/generate")
	public TurDataGroupModel generate(@PathVariable int dataGroupId)
			throws JSONException, IOException, SAXException, TikaException {

		TurDataGroup turDataGroup = turDataGroupRepository.findById(dataGroupId);
		List<TurDataGroupSentence> turDataSentences = this.turDataGroupSentenceRepository
				.findByTurDataGroup(turDataGroup);

		String modelFileName = Integer.toString(turDataGroup.getId());
		String trainFilePath = String.format("store/ml/train/%s.train", modelFileName);
		String modelFilePath = String.format("store/ml/model/%s.model", modelFileName);

		StringBuffer trainSB = new StringBuffer();

		for (TurDataGroupSentence vigTrainDocSentence : turDataSentences) {
			if (vigTrainDocSentence.getTurMLCategory() != null) {
				trainSB.append(vigTrainDocSentence.getTurMLCategory().getInternalName() + " ");
				trainSB.append(vigTrainDocSentence.getSentence().toString().replaceAll("[\\t\\n\\r]", " ").trim());
				trainSB.append("\n");
			}
		}
		PrintWriter out = new PrintWriter(trainFilePath);
		out.println(trainSB.toString().trim());
		out.close();

		///////////////
		try (BufferedReader br = new BufferedReader(new FileReader(trainFilePath))) {
			String line;
			while ((line = br.readLine()) != null) {

				// Whitespace tokenize entire string

				String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(line);

				if (tokens.length > 1) {
					///
				} else {
					// Empty lines: " + tokens.toString());
				}
			}
		}

		DoccatModel model = null;

		InputStream dataIn = null;
		try {
			InputStreamFactory isf = new InputStreamFactory() {
				public InputStream createInputStream() throws IOException {
					return new FileInputStream(trainFilePath);
					// return new
					// ByteArrayInputStream(trainSB.toString().getBytes(StandardCharsets.UTF_8));
				}
			};

			Charset charset = Charset.forName("UTF-8");
			ObjectStream<String> lineStream = new PlainTextByLineStream(isf, charset);
			ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream);

			DoccatFactory factory = new DoccatFactory();

			model = DocumentCategorizerME.train("en", sampleStream, TrainingParameters.defaultParams(), factory);

		} catch (IOException e) {
			// Failed to read or parse training data, training failed
			e.printStackTrace();
		} finally {
			if (dataIn != null) {
				try {
					dataIn.close();
				} catch (IOException e) {
					// Not an issue, training already finished.
					// The exception should be logged and investigated
					// if part of a production system.
					e.printStackTrace();
				}
			}
		}
		OutputStream modelOut = null;
		try {
			modelOut = new BufferedOutputStream(new FileOutputStream(modelFilePath));
			model.serialize(modelOut);
		} catch (IOException e) {
			// Failed to save model
			e.printStackTrace();
		} finally {
			if (modelOut != null) {
				try {
					modelOut.close();
				} catch (IOException e) {
					// Failed to correctly save model.
					// Written model might be invalid.
					e.printStackTrace();
				}
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
	}
}
