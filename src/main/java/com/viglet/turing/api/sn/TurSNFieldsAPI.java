package com.viglet.turing.api.sn;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.se.facet.TurSEFacetMaps;
import com.viglet.turing.se.field.TurSEFieldMaps;

@Component
@Path("sn/field")
public class TurSNFieldsAPI {

	@Autowired
	TurSNSiteRepository turSNSiteRepository;

	@GET
	@Path("create")
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurSNSite> create() throws JSONException, ClientProtocolException, IOException {
		TurSEFieldMaps turSEFieldMaps = new TurSEFieldMaps();
		for (String field : turSEFieldMaps.getFieldMaps().keySet()) {
			this.createField(field);
		}

		TurSEFacetMaps turSEFacetMaps = new TurSEFacetMaps();
		for (String facet : turSEFacetMaps.getFacetMaps().keySet()) {
			this.createField(facet);
		}
		return this.turSNSiteRepository.findAll();
	}

	public void createField(String field) throws ClientProtocolException, IOException {
		CloseableHttpClient client = HttpClients.createDefault();
		JSONObject jsonAddField = new JSONObject();
		jsonAddField.put("name", field);
		
		jsonAddField.put("indexed", true);
		jsonAddField.put("stored", true);
		System.out.println(field);
		if (field.trim().toLowerCase().startsWith("turing_entity_")) {
			jsonAddField.put("type", "string");
			jsonAddField.put("multiValued", true);
			System.out.println("true");
		} else {
			jsonAddField.put("type", "text_general");
			jsonAddField.put("multiValued", false);
			System.out.println("false");
		}
		JSONObject json = new JSONObject();
		json.put("add-field", jsonAddField);

		HttpPost httpPost = new HttpPost("http://localhost:8983/solr/turing/schema");
		StringEntity entity = new StringEntity(json.toString());
		httpPost.setEntity(entity);
		httpPost.setHeader("Accept", "application/json");
		httpPost.setHeader("Content-type", "application/json");

		CloseableHttpResponse response = client.execute(httpPost);
		System.out.println(response.toString());
		client.close();
	}
}