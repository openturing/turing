package com.viglet.turing.api.ml.model;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
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
import javax.ws.rs.core.Response;

import org.apache.tika.exception.TikaException;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.viglet.turing.persistence.model.ml.TurMLModel;
import com.viglet.turing.persistence.model.storage.TurDataSentence;
import com.viglet.turing.persistence.service.ml.TurMLModelService;
import com.viglet.turing.persistence.service.storage.TurDataSentenceService;

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

@Path("/ml/model")
public class TurMLModelAPI {
	TurDataSentenceService turDataSentenceService = new TurDataSentenceService();
	TurMLModelService turMLModelService = new TurMLModelService();

	@GET
	@Produces("application/json")
	public List<TurMLModel> list() throws JSONException {
		return turMLModelService.listAll();
	}

	@GET
	@Path("{mlModelId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLModel dataGroup(@PathParam("mlModelId") int id) throws JSONException {
		return turMLModelService.get(id);
	}

	@Path("/{mlModelId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLModel update(@PathParam("mlModelId") int id, TurMLModel turMLModel) throws Exception {
		TurMLModel turMLModelEdit = turMLModelService.get(id);
		turMLModelEdit.setInternalName(turMLModel.getInternalName());
		turMLModelEdit.setName(turMLModel.getName());
		turMLModelEdit.setDescription(turMLModel.getDescription());
		turMLModelService.save(turMLModelEdit);
		return turMLModelEdit;
	}

	@Path("/{mlModelId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("mlModelId") int id) throws Exception {
		return turMLModelService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurMLModel add(TurMLModel turMLModel) throws Exception {
		turMLModelService.save(turMLModel);
		return turMLModel;

	}

	@GET
	@Path("evaluation")
	@Produces("application/json")
	public Response evaluation() throws JSONException {
		File modelFile = new File("/Users/alexandreoliveira/Desktop/mymodel.bin");
		InputStream modelStream;
		DoccatModel m = null;
		try {
			modelStream = new FileInputStream(modelFile);
			m = new DoccatModel(modelStream);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String[] inputText = {
				"Republicans in Congress will start this week on an obstacle course even more arduous than health care: the first overhaul of the tax code in three decades." };
		DocumentCategorizerME myCategorizer = new DocumentCategorizerME(m);
		double[] outcomes = myCategorizer.categorize(inputText);
		String category = myCategorizer.getBestCategory(outcomes);

		JSONObject json = new JSONObject().put("text", inputText).put("category", category);

		return Response.status(200).entity(json.toString()).build();
	}

	@GET
	@Path("generate")
	@Produces("application/json")
	public Response generate() throws JSONException, IOException, SAXException, TikaException {

		List<TurDataSentence> turDataSentences = turDataSentenceService.listAll();

		StringBuffer trainSB = new StringBuffer();

		boolean catVariables = true;
		for (TurDataSentence vigTrainDocSentence : turDataSentences) {
			if (catVariables) {
				trainSB.append("Cat01 ");
				catVariables = false;
			} else {
				trainSB.append("Cat02 ");
				catVariables = true;
			}
			trainSB.append(vigTrainDocSentence.getSentence().toString().replaceAll("[\\t\\n\\r]", " ").trim());
			trainSB.append("\n");
		}
		PrintWriter out = new PrintWriter("/Users/alexandreoliveira/Desktop/en-sentiment.train");
		out.println(trainSB.toString().trim());
		out.close();

		///////////////
		try (BufferedReader br = new BufferedReader(
				new FileReader("/Users/alexandreoliveira/Desktop/en-sentiment.train"))) {
			String line;
			System.out.println("Testando arquivo");
			while ((line = br.readLine()) != null) {

				// Whitespace tokenize entire string

				String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(line);

				if (tokens.length > 1) {
					///
				} else {
					System.out.println("Empty lines: " + tokens.toString());
				}
			}
			System.out.println("Fim do teste");
		}
		////////
		DoccatModel model = null;

		InputStream dataIn = null;
		try {
			InputStreamFactory isf = new InputStreamFactory() {
				public InputStream createInputStream() throws IOException {
					return new FileInputStream("/Users/alexandreoliveira/Desktop/en-sentiment.train");
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
			String modelFile = "/Users/alexandreoliveira/Desktop/mymodel.bin";
			modelOut = new BufferedOutputStream(new FileOutputStream(modelFile));
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
		return null;
	}

}
