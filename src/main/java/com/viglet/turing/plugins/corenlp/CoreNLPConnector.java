package com.viglet.turing.plugins.corenlp;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.xml.transform.TransformerException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.nlp.VigNLPResults;
import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.persistence.model.TurNLPEntity;
import com.viglet.turing.plugins.nlp.NLPImpl;

import java.util.*;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLPClient;

public class CoreNLPConnector implements NLPImpl {
	public static int PRETTY_PRINT_INDENT_FACTOR = 4;
	List<TurNLPEntity> nlpEntities = null;
	Map<String, JSONArray> entityList = new HashMap<String, JSONArray>();
	public JSONObject json;
	VigService vigService = null;

	@SuppressWarnings("unchecked")
	public CoreNLPConnector(VigService vigService) {
		this.vigService = vigService;

		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryNLPEntity = em
				.createQuery(
						"SELECT sne FROM VigServicesNLPEntity sne, VigService s where s.id = :id_service and sne.vigService = s and sne.enabled = :enabled ")
				.setParameter("id_service", vigService.getId()).setParameter("enabled", 1);

		nlpEntities = queryNLPEntity.getResultList();
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public VigNLPResults request(VigService vigService, String request) throws MalformedURLException, IOException {

		String props = "{\"tokenize.whitespace\":\"true\",\"annotators\":\"tokenize,ssplit,pos,ner\",\"outputFormat\":\"json\"}";

		String queryParams = String.format("properties=%s", URLEncoder.encode(props, "utf-8"));

		URL serverURL = new URL("http", vigService.getHost(), vigService.getPort(), "/?" + queryParams);

		System.out.println("URL:" + serverURL.toString());

		HttpClient httpclient = HttpClients.createDefault();
		HttpPost httppost = new HttpPost(serverURL.toString());

		StringEntity stringEntity = new StringEntity(request);
		httppost.setEntity(stringEntity);

		HttpResponse response = httpclient.execute(httppost);
		HttpEntity entity = response.getEntity();

		if (entity != null) {
			InputStream instream = entity.getContent();
			BufferedReader rd = new BufferedReader(new InputStreamReader(instream, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			this.json = new JSONObject(jsonText);
			System.out.println(this.json);
			try {
			} finally {
				instream.close();
			}
		}

		VigNLPResults vigNLPResults = new VigNLPResults();
		vigNLPResults.setJsonResult(this.getJSON());
		vigNLPResults.setTurNLPEntities(nlpEntities);

		return vigNLPResults;

	}

	public JSONObject getJSON() {
		JSONObject jsonObject = new JSONObject();
		this.getEntities();
		for (TurNLPEntity entity : nlpEntities) {
			jsonObject.put(entity.getTurEntity().getCollectionName(), this.getEntity(entity.getName()));
		}
		jsonObject.put("nlp", "CoreNLP");

		System.out.println(jsonObject.toString());
		return jsonObject;
	}

	public JSONArray getEntities() {
		JSONArray jsonEntity = new JSONArray();
		JSONArray sentences = this.json.getJSONArray("sentences");

		StringBuilder sb = new StringBuilder();
		List tokenList = new ArrayList<>();

		for (int i = 0; i < sentences.length(); i++) {
			JSONObject sentence = (JSONObject) sentences.get(i);
			JSONArray tokens = sentence.getJSONArray("tokens");

			// traversing the words in the current sentence, "O" is a sensible
			// default to initialise
			// tokens to since we're not interested in unclassified / unknown
			// things..
			String prevNeToken = "O";
			String currNeToken = "O";
			boolean newToken = true;

			for (int t = 0; t < tokens.length(); t++) {

				JSONObject token = (JSONObject) tokens.get(t);

				currNeToken = token.getString("ner");
				System.out.println("NER: " + currNeToken);
				String word = token.getString("word");
				// Strip out "O"s completely, makes code below easier to
				// understand
				if (currNeToken.equals("O")) {
					// LOG.debug("Skipping '{}' classified as {}", word,
					// currNeToken);
					if (!prevNeToken.equals("O") && (sb.length() > 0)) {
						handleEntity(prevNeToken, sb, tokenList);
						newToken = true;
					}
					continue;
				}

				if (newToken) {
					prevNeToken = currNeToken;
					newToken = false;
					sb.append(word);
					continue;
				}

				if (currNeToken.equals(prevNeToken)) {
					sb.append(" " + word);
				} else {
					// We're done with the current entity - print it out and
					// reset
					// TODO save this token into an appropriate ADT to return
					// for useful processing..
					this.handleEntity(prevNeToken, sb, tokenList);
					newToken = true;
				}
				prevNeToken = currNeToken;

			}
			if (!newToken && (sb.length() > 0)) {
				this.handleEntity(prevNeToken, sb, tokenList);
			}
		}
		return jsonEntity;
	}

	public JSONArray getEntity(String entity) {
		System.out.println("Entity getEntity: " + entity);
		return entityList.get(entity);
	}

	public VigNLPResults retrieve(String text) throws TransformerException, Exception {
		return this.request(this.vigService, text);
	}

	private void handleEntity(String inKey, StringBuilder inSb, List inTokens) {
		System.out.println(inSb + " is a " + inKey);
		inTokens.add(new EmbeddedToken(inKey, inSb.toString()));

		if (entityList.containsKey(inKey)) {
			entityList.get(inKey).put(inSb.toString());
		} else {
			entityList.put(inKey, new JSONArray().put(inSb.toString()));
		}

		System.out.println("entityList Size:" + entityList.size());
		inSb.setLength(0);
	}

	class EmbeddedToken {

		private String name;
		private String value;

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		public EmbeddedToken(String name, String value) {
			super();
			this.name = name;
			this.value = value;
		}
	}
}
