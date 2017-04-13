package com.viglet.turing.test;

import java.io.IOException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;

public class SolrJExample {
	public StringBuilder consulta() throws SolrServerException {
		SolrServer server = new HttpSolrServer("http://localhost:8983/solr/films");
		SolrQuery query = new SolrQuery();
		query.setQuery("directed_by:Gary Lennon");
		StringBuilder sb = new StringBuilder();
		QueryResponse queryResponse = server.query(query);
		for (SolrDocument document : queryResponse.getResults()) {
			sb.append(document);			
		}	
		
		return sb;
	}
	
	public boolean inserir() throws SolrServerException, IOException {
		SolrServer solr = new HttpSolrServer("http://localhost:8983/solr/films");
		SolrQuery query = new SolrQuery();
		SolrInputDocument document = new SolrInputDocument();
		document.addField("id", "552199");
		document.addField("name", "Gouda cheese wheel");
		document.addField("price", "49.99");
		UpdateResponse response = solr.add(document);
		 
		// Remember to commit your changes!
		 
		solr.commit();
		
		return true;
	}
}