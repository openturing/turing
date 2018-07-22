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

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.tika.exception.TikaException;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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

@Component
@Path("ml/data/group/{dataGroupId}/model")
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

	@GET
	@Produces("application/json")
	public List<TurDataGroupModel> list(@PathParam("dataGroupId") int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupRepository.findById(dataGroupId);
		return this.turDataGroupModelRepository.findByTurDataGroup(turDataGroup);
	}

	@Path("{dataGroupModelId}")
	@GET
	@Produces("application/json")
	public TurDataGroupModel mlSolution(@PathParam("dataGroupId") int dataGroupId,
			@PathParam("dataGroupModelId") int id) throws JSONException {
		return this.turDataGroupModelRepository.findById(id);
	}

	@Path("/{dataGroupModelId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupModel update(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupModelId") int id,
			TurDataGroupModel turDataGroupModel) throws Exception {
		TurDataGroupModel turDataGroupModelEdit = this.turDataGroupModelRepository.findById(id);
		turDataGroupModelEdit.setTurMLModel(turDataGroupModel.getTurMLModel());
		this.turDataGroupModelRepository.save(turDataGroupModelEdit);
		return turDataGroupModelEdit;
	}

	@Path("{dataGroupModelId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupModelId") int id) {
		this.turDataGroupModelRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupModel add(@PathParam("dataGroupId") int dataGroupId, TurMLModel turMLModel) throws Exception {
		TurDataGroupModel turDataGroupModel = new TurDataGroupModel();
		TurDataGroup turDataGroup = this.turDataGroupRepository.findById(dataGroupId);
		turDataGroupModel.setTurMLModel(turMLModel);
		turDataGroupModel.setTurDataGroup(turDataGroup);
		this.turDataGroupModelRepository.save(turDataGroupModel);
		return turDataGroupModel;

	}

	@GET
	@Path("generate")
	@Produces("application/json")
	public TurDataGroupModel generate(@PathParam("dataGroupId") int dataGroupId)
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
