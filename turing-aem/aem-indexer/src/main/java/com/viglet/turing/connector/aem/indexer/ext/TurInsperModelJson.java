package com.viglet.turing.connector.aem.indexer.ext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAEMIndexerTool;
import com.viglet.turing.connector.aem.indexer.bean.TurInsperModelJsonBean;
import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class TurInsperModelJson implements ExtContentInterface {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	@Override
	public List<TurAttrDef> consume(AemObject aemObject, IHandlerConfiguration config, TurAEMIndexerTool turAEMIndexerTool) {
		logger.debug("Executing TurInsperModelJson");
		List<TurAttrDef> turAttrDefList = new ArrayList<>();
		HttpClient client = HttpClient.newBuilder()
				.authenticator(new Authenticator() {
					@Override
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(turAEMIndexerTool.getUsername(),
								turAEMIndexerTool.getPassword().toCharArray());
					}
				})
				.build();

		try {
			String url = TurContentUrl.getURL(aemObject,config).replaceAll(".html$",".model.json");
			HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(url)).build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			String json = response.body();
			ObjectMapper objectMapper = new ObjectMapper()
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			TurInsperModelJsonBean model = objectMapper.readValue(json, TurInsperModelJsonBean.class);
			if (model.getFragmentPath() != null) {
				turAttrDefList.add(new TurAttrDef("fragmentPath", TurMultiValue
						.singleItem(model.getFragmentPath())));
			}
			if (model.getTemplateName() != null) {
				turAttrDefList.add(new TurAttrDef("templateName",
						TurMultiValue.singleItem(model.getTemplateName())));
			}
			if (model.getGenericContentFragmentData() != null
					&& model.getGenericContentFragmentData().getDescription() != null) {
				turAttrDefList.add(new TurAttrDef("abstract",TurMultiValue
						.singleItem(model.getGenericContentFragmentData().getDescription())));
			}
			return turAttrDefList;
		} catch (URISyntaxException | InterruptedException | IOException e) {
			logger.error(e.getMessage(),e);
		}
        return new ArrayList<>();

	}
}
