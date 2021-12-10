package com.viglet.turing.wem.dsm;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.pagination.TurSNPagination;
import com.viglet.turing.client.sn.pagination.TurSNPaginationItem;
import com.viglet.turing.client.sn.pagination.TurSNPaginationType;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;

public class TurDSMTest {
	private static Logger logger = Logger.getLogger(TurSNServer.class.getName());

	public TurDSMTest() {
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
		/*
		 * SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		 * System.out.println(sdf.format(new Date()));
		 */

		HttpTurSNServer turSNServer = null;
		try {
			turSNServer = new HttpTurSNServer(new URL("http://10.90.121.109"), "Sample", "default");
		} catch (MalformedURLException e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		TurSNQuery query = new TurSNQuery();
		ArrayList<String> facets = new ArrayList<>();

		facets.add("host:enwikipediaorg");
		facets.add("type:Page");

		query.setQuery("*");
		query.setFieldQueries(facets);
		query.setRows(10);
		query.setSortField(TurSNQuery.ORDER.asc);
		query.setPageNumber(2);

		QueryTurSNResponse response = turSNServer.query(query);
		TurSNDocumentList turSNResults = response.getResults();
		TurSNPagination turSNPagination = response.getPagination();

		TurSNFacetFieldList facetFields1 = response.getFacetFields();
		// facetFields1.getTurSNFacetFields()
		response.getFacetFields().getFacetWithRemovedValues().ifPresent(facetToRemove -> {
			System.out.println("---");
			System.out.println(facetToRemove.getLabel());
			facetToRemove.getValues().forEach(value -> {
				System.out.println(value.getLabel());
				value.getQueryParams().get().entrySet().forEach(queryParam -> {
					System.out.println(queryParam.getKey() + " = " + queryParam.getValue());
				});
			});
		});
		response.getFacetFields().forEach(facetFields -> {
			// facetFields.
			System.out.println("Facet: " + facetFields.getLabel());
			facetFields.getValues().forEach(facetField -> System.out
					.println(facetField.getLabel() + facetField.getApiURL() + "(" + facetField.getCount() + ")"));
		});
		System.out.println("");
		System.out.println("---");

		turSNPagination.getAllPages().forEach(page -> {
			System.out.print(page.getLabel() + "-");
			System.out.println(page.getPageNumber());
			// Object obj = page.getQueryParams().isPresent()
			if (page.getQueryParams().isPresent()) {
				page.getQueryParams().get().entrySet().forEach(queryParam -> {
					System.out.print(queryParam.getKey() + " = ");
					System.out.println(queryParam.getValue());
				});
			} else
				System.out.println("------------------Ruim---------------------------------------------");
		});
		System.out.println("");
		System.out.println("---");
		turSNPagination.getLastPage().ifPresent(page -> System.out.println(page.getLabel()));

		System.out.println("---");

//		response.getFacetFields().forEach(facetFields -> {
//			System.out.println("Facet: " + facetFields.getLabel());
//			facetFields.getValues().forEach(
//					facetField -> System.out.println(facetField.getLabel() + "(" + facetField.getCount() + ")"));
//		});
//		response.getFacetFields().getTurSNFacetToRemove().ifPresent(facetToRemove -> {
//			System.out.println("---");
//			System.out.println(facetToRemove.getLabel());
//			facetToRemove.getValues().forEach(value -> System.out.println(value.getLabel()));
//		});

		System.out.println(turSNPagination.getPageNumberList());

		List<TurSNPaginationItem> pages = turSNPagination.getAllPages();
		if (pages.size() > 1) {

			for (TurSNPaginationItem page : pages) {
				String linkLabel = page.getType().equals(TurSNPaginationType.PAGE)
						? Integer.toString(page.getPageNumber())
						: page.getLabel();
				System.out.println(
						"<a href=\"" + "?text1=" + "*" + "&page1=" + page.getPageNumber() + "\">" + linkLabel + "</a>");
			}
		} else {
			System.out.println("Only 1 page");
		}

		/*
		 * TurSNQuery query = new TurSNQuery(); query.setQuery("*"); query.setRows(10);
		 * query.setSortField(TurSNQuery.ORDER.asc); query.setPageNumber(1);
		 * 
		 * QueryTurSNResponse response = turSNServer.query(query); TurSNDocumentList
		 * turSNResults = response.getResults(); TurSNPagination turSNPagination =
		 * response.getPagination();
		 * System.out.println(turSNPagination.getAllPages().size());
		 */
		// turSNPagination.getAllPages().forEach(page -> {
		// System.out.println(page.getLabel());
		// });
		//
		// //System.out.println(turSNPagination.getCurrentPage().getPageNumber());
		// System.out.println(turSNPagination.getPageNumberList());
		// System.out.println(turSNPagination.getLastPage());

		/*
		 * for (TurSNDocument document : turSNResults.getTurSNDocuments()) { Map doc =
		 * document.getContent().getFields(); for (Object keyObj : doc.keySet()) {
		 * String key = (String) keyObj; //String value = (doc.get(key) != null) ?
		 * (String) doc.get(key) : ""; System.out.println("<BR>" + key + " = " +
		 * doc.get(key));
		 * 
		 * 
		 * } }
		 * 
		 */
	}

}
