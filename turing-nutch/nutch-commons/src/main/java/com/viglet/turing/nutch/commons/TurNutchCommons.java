package com.viglet.turing.nutch.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class TurNutchCommons {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	
	public static final String ID_FIELD = "id";
	public static final String CONTENT_FIELD = "content";
	public static final String TITLE_FIELD = "title";
	public static final String TEXT_FIELD = "text";
	public static final String TIMESTAMP_FIELD = "tstamp";
	public static final String TYPE_FIELD = "type";
	public static final String CONNECTOR_FIELD = "source_apps";
	public static final Locale LOCALE_DEFAULT_VALUE = Locale.US;
	public static final String TYPE_DEFAULT_VALUE = "Page";
	public static final String CONNECTOR_DEFAULT_VALUE = "Nutch";
	public static final String USERNAME_DEFAULT_VALUE = "admin";
	public static final String PASSWORD_DEFAULT_VALUE = "admin";
	public static final String SITE_DEFAULT_VALUE = "Sample";
	public static final String META_TAG_VALUE = "metatag.";
	public static final String TURING_SERVER_DEFAULT_VALUE = "http://localhost:2700";
	public static final char TAB = '\t';
	public static final String SLASH = "/";
	
	public static String stripNonCharCodepoints(String input) {
		StringBuilder returnValue = new StringBuilder();
		char ch;

		for (int i = 0; i < input.length(); i++) {
			ch = input.charAt(i);
			if (ch % 0x10000 != 0xffff && // 0xffff - 0x10ffff range step 0x10000
					ch % 0x10000 != 0xfffe && // 0xfffe - 0x10fffe range
					(ch <= 0xfdd0 || ch >= 0xfdef) && // 0xfdd0 - 0xfdef
					(ch > 0x1F || ch == 0x9 || ch == 0xa || ch == 0xd)) {

				returnValue.append(ch);
			}
		}

		return returnValue.toString();
	}

	public static void push(TurSNJobItems turSNJobItems, boolean auth, int totalAdds, String username, String password,
			String url, String site) throws IOException {
		if (!turSNJobItems.getTuringDocuments().isEmpty()) {
			int totalCreate = 0;
			int totalDelete = 0;

			for (TurSNJobItem turSNJobItem : turSNJobItems.getTuringDocuments()) {
				TurSNJobAction turSNJobAction = turSNJobItem.getTurSNJobAction();
				switch (turSNJobAction) {
				case CREATE:
					totalCreate++;
					break;
				case DELETE:
					totalDelete++;
					break;
				}
			}

			logger.info(String.format("Indexing %d/%d documents", totalCreate, totalAdds));
			logger.info(String.format("Deleting %d documents", totalDelete));

            ObjectMapper mapper = new ObjectMapper();
			String jsonResult = mapper.writeValueAsString(turSNJobItems);

			Charset utf8Charset = StandardCharsets.UTF_8;
			Charset customCharset = StandardCharsets.UTF_8;

			ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

			// decode UTF-8
			CharBuffer data = utf8Charset.decode(inputBuffer);

			// encode
			ByteBuffer outputBuffer = customCharset.encode(data);

			final HttpPost httpPost = getHttpPost(url, site, outputBuffer);

			if (auth) {
				basicAuth(httpPost, username, password);
			}
			try (CloseableHttpClient client = HttpClients.createDefault();
					CloseableHttpResponse ignored = client.execute(httpPost)) {
				turSNJobItems.getTuringDocuments().clear();
			}
		}
	}

	private static HttpPost getHttpPost(String url, String site, ByteBuffer outputBuffer) {
		byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
		String jsonUTF8 = new String(outputData);

		HttpPost httpPost = new HttpPost(String.format("%s/api/sn/%s/import", url, site));
		StringEntity entity = new StringEntity(jsonUTF8, StandardCharsets.UTF_8);
		httpPost.setEntity(entity);
		httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
		httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
		httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());
		return httpPost;
	}

	private static void basicAuth(HttpPost httpPost, String username, String password) {
		if (username != null) {
			String auth = String.format("%s:%s", username, password);
			String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			String authHeader = "Basic " + encodedAuth;
			httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
		}
	}
}
