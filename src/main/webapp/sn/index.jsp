<%@page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"
	import="    java.util.*,
                java.io.*,
				org.apache.http.HttpResponse,
				org.apache.http.client.ClientProtocolException,
				org.apache.http.client.methods.HttpGet,
				org.apache.http.impl.client.DefaultHttpClient,
				org.json.*,
				org.apache.commons.lang.*,
				java.net.URLEncoder"%>


<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
<head>
<meta charset="utf-8" />
<meta http-equiv="content-type"
	content="application/xhtml+xml; charset=UTF-8" />
<meta http-equiv="X-UA-Compatible" content="IE=edge" />
<meta name="viewport"
	content="width=device-width, initial-scale=1.0, minimum-scale=1.0" />
<title>Semantic Navigation</title>
<!-- Page styles -->
<link rel="stylesheet"
	href="https://fonts.googleapis.com/css?family=Roboto:regular,bold,italic,thin,light,bolditalic,black,medium&amp;lang=en" />
<link rel="stylesheet"
	href="https://fonts.googleapis.com/icon?family=Material+Icons" />
<link rel="stylesheet"
	href="https://code.getmdl.io/1.2.0/material.min.css" />
<link rel="stylesheet" href="styles.css" />


<!-- Add to homescreen for Chrome on Android -->
<meta name="mobile-web-app-capable" content="yes" />
<link rel="icon" sizes="192x192" href="../images/android-desktop.png" />

<!-- Add to homescreen for Safari on iOS -->
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-status-bar-style" content="black" />
<meta name="apple-mobile-web-app-title" content="Viglet Turing" />
<link rel="apple-touch-icon-precomposed"
	href="../images/ios-desktop.png" />

<!-- Tile icon for Win8 (144x144 + tile color) -->
<meta name="msapplication-TileImage"
	content="../images/touch/ms-touch-icon-144x144-precomposed.png" />
<meta name="msapplication-TileColor" content="#3372DF" />

<link rel="shortcut icon" href="../images/favicon.png" />
<style>
#view-source {
	position: fixed;
	display: block;
	right: 0;
	bottom: 0;
	margin-right: 40px;
	margin-bottom: 40px;
	z-index: 900;
}
</style>
</head>


<%!private String otsnFrontendServer;
	private String otsnFrontendPort;
	private String index;
	private String usuarioLogadoQuery = "";
	private String specialWordsQuery = "";
	private String qOriginal = "";

	private void parsePropertiesFromResource(HttpServletRequest request) throws Throwable {
		//String propertiesBody = ConfigUtil.getGenericResourceValue("Properties", "OTSN");
		//StringReader propsBodyStream = new StringReader(propertiesBody);
		//Properties properties = new Properties();
		//properties.load(propsBodyStream);
		otsnFrontendServer = request.getServerName();
		otsnFrontendPort = Integer.toString(request.getServerPort());
		index = "turing";
	}

	private String replaceUrlSearch(String url) throws Throwable {
		url = url.replaceAll("/" + index + "/api/otsn/search/theme/json", "/turing/sn");
		return url;
	}

	public String otsnPagination(JSONObject paginationObject) throws Throwable {
		StringBuilder paginationSB = new StringBuilder();
		JSONArray jsonArray = paginationObject.getJSONArray("otsn:page");
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonResult = (JSONObject) jsonArray.get(i);
			String otsnTitle = jsonResult.getString("text");
			if (jsonResult.has("href")) {
				String otsnURL = replaceUrlSearch(jsonResult.getString("href"));
				paginationSB.append("<a href=\"" + otsnURL + "\" style='font-size:20px'>" + otsnTitle + "</a> | ");
			} else {
				paginationSB.append("<span style='font-size:20px'>" + otsnTitle + "</span> | ");
			}
		}
		return paginationSB.toString();
	}

	public String otsnFacet(JSONObject facetObject) throws Throwable {
		StringBuilder facetSB = new StringBuilder();
		facetSB.append(
				"<div class=\"demo-options mdl-card mdl-card--expand mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--3-col-tablet mdl-cell--12-col-desktop\"><div class=\"mdl-card__supporting-text mdl-card--expand\">");
		facetSB.append("<h3>" + facetObject.getJSONObject("rdfs:label").getString("text") + "</h3>");
		facetSB.append("<ul class=\"demo-list-item mdl-list\">");
		JSONArray jsonONArray = facetObject.getJSONObject("otsn-facet").getJSONArray("facet");
		for (int i = 0; i < jsonONArray.length(); i++) {
			facetSB.append("<li class=\"mdl-list__item\" style=\"padding: 0px;min-height: 30px;\">");
			facetSB.append("<span class=\"mdl-list__item-primary-content\">");
			JSONObject jsonResult = (JSONObject) jsonONArray.get(i);
			String otsnFacetTitle = jsonResult.getString("label");
			String otsnFacetCount = jsonResult.getString("facet-count");
			String otsnFacetURL = replaceUrlSearch(jsonResult.getString("facet-link"));

			facetSB.append("<a href=\"" + otsnFacetURL + "\">" + otsnFacetTitle + " (" + otsnFacetCount + ")</a>");

		}
		facetSB.append("</ul>");
		facetSB.append("</div></div>");
		return facetSB.toString();
	}%>
<div style="margin: 20px">

	<%
		String qRequest = "";
		boolean hasQRequest = false;
		int total = 0;
		String termoDeBusca = "";
		String foundMsg = "";
		String spellChecker = null;
		JSONObject jsonObject = null;
		try {
			if (request.getParameter("q") != null) {
				hasQRequest = true;
				qRequest = request.getParameter("q");

				String fq = "";
				if (request.getParameterValues("fq[]") != null) {
					String[] fqArray = request.getParameterValues("fq[]");
					for (int i = 0; i < fqArray.length; i++) {
						fq += "&fq[]=" + fqArray[i];
					}
					//fq = new String(fq.getBytes("iso-8859-1"), "UTF-8").replaceAll(" ", "%20");
					fq = fq.replaceAll(" ", "%20");
				}
				String p = null;
				if (request.getParameter("p") != null) {
					p = request.getParameter("p");
				}
				String f = request.getParameter("f");
				termoDeBusca = qRequest != null ? qRequest : "";
				parsePropertiesFromResource(request);
				String q = qRequest;
				if (f == null) {
					String returnSpellChecker = q;
					if (returnSpellChecker != null) {
						spellChecker = q;
						termoDeBusca = q = returnSpellChecker;
					}
				}

				DefaultHttpClient httpClient = new DefaultHttpClient();
				qOriginal = q;
				q = URLEncoder.encode(q, "UTF-8");

				String otsnURL = "http://" + otsnFrontendServer + ":" + otsnFrontendPort + "/" + index
						+ "/api/otsn/search/theme/json?sort=relevant&_setlocale=pt&q=" + q + usuarioLogadoQuery;
				if (fq != null) {
					otsnURL += fq;
				}
				if (p != null) {
					otsnURL += "&p=" + p;
				}

				//out.println("URL: " + otsnURL + "<br>");
				HttpGet getRequest = new HttpGet(otsnURL);
				getRequest.addHeader("accept", "application/json");

				HttpResponse responseSN = httpClient.execute(getRequest);

				if (responseSN.getStatusLine().getStatusCode() != 200) {
					throw new RuntimeException(
							"Failed : HTTP error code : " + responseSN.getStatusLine().getStatusCode());
				}

				BufferedReader br = new BufferedReader(
						new InputStreamReader((responseSN.getEntity().getContent())));

				String output;
				StringBuilder jsonSB = new StringBuilder();
				while ((output = br.readLine()) != null) {
					jsonSB.append(output);
				}

				String json = jsonSB.toString();

				jsonObject = new JSONObject(json);

				//foundMsg = "tema".equals(origem) ? " tema " + dipTema.getNome().toLowerCase() : ("segmento".equals(origem) ? " segmento " + dipSegmento.getNome().toLowerCase() : " Portal Sebrae");
				total = jsonObject.getJSONObject("rdf:Description").getJSONObject("otsn:query-context")
						.getInt("otsn:count");
			}
	%>
	<form method="get">
		<div
			class="mdl-textfield mdl-js-textfield mdl-textfield--floating-label"
			style="width: calc(100% - 250px);">
			<input class="mdl-textfield__input" id="q" name="q"
				style="font-size: 40px" value='<%=qRequest%>'> <label
				class="mdl-textfield__label" for="sample3">Pesquise...</label>
		</div>
		<button
			class="mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent"
			style="font-size: 20px" type="submit">Pesquisar</button>
	</form>
	<%
		if (hasQRequest == true) {
				if (jsonObject.getJSONObject("rdf:Description").has("otsn:pagination")
						&& jsonObject.getJSONObject("rdf:Description").getJSONObject("otsn:pagination")
								.getJSONArray("otsn:page").length() > 1) {
					out.println(otsnPagination(
							jsonObject.getJSONObject("rdf:Description").getJSONObject("otsn:pagination")));
				}
	%>
	<h2>Resultado da busca</h2>
	<h5>
		Foram encontrados <strong id="totalResultados"><%=total%></strong>
		resultados para o termo "<%=termoDeBusca%>".
	</h5>
</div>
<div style="margin: 20px;">
	<table>
		<tr>
			<td style="vertical-align: top;">
				<%
					if (!jsonObject.getJSONObject("rdf:Description").getJSONObject("otsn:widget")
									.isNull("otsn:facet-widget")) {
								JSONObject jsonWidgetObject = jsonObject.getJSONObject("rdf:Description")
										.getJSONObject("otsn:widget").getJSONObject("otsn:facet-widget");

								if (jsonWidgetObject.has("otsn:facet-to-remove")) {
				%>
				<div
					class="demo-options mdl-card mdl-card--expand mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--3-col-tablet mdl-cell--12-col-desktop">
					<div class="mdl-card__supporting-text mdl-card--expand">
						<h3>Filtros Aplicados</h3>
						<ul class="demo-list-item mdl-list">
							<%
								JSONObject jsonWidgetON = jsonWidgetObject.getJSONObject("otsn:facet-to-remove");
												JSONArray jsonONArray = jsonWidgetON.getJSONObject("otsn-facet").getJSONArray("facet");
												for (int i = 0; i < jsonONArray.length(); i++) {
													JSONObject jsonResult = (JSONObject) jsonONArray.get(i);
													String otsnONTitle = jsonResult.getString("label");
													String otsnONURL = replaceUrlSearch(jsonResult.getString("facet-link"));
							%>
							<li class="mdl-list__item"
								style="padding: 0px; min-height: 30px;"><span
								class="mdl-list__item-primary-content"> <a
									href="<%=otsnONURL%>"> <%=otsnONTitle%> (Remover)
								</a></li>
							<%
								}
							%>
						</ul>
					</div>
				</div> <%
 	}

 				if (jsonWidgetObject.has("sebna:theme")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("sebna:theme")));
 				}

 				if (jsonWidgetObject.has("sebna:segment")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("sebna:segment")));

 				}

 				if (jsonWidgetObject.has("sebna:state")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("sebna:state")));

 				}
 				if (jsonWidgetObject.has("sebna:type")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("sebna:type")));

 				}

 				if (jsonWidgetObject.has("otsn:people")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("otsn:people")));

 				}

 				/*
 				if (jsonWidgetObject.has("otsn:organization")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("otsn:organization")));
 				
 				}
 				
 				if (jsonWidgetObject.has("otsn:place")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("otsn:place")));
 				
 				}
 				*/

 				if (jsonWidgetObject.has("otsn:iptc")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("otsn:iptc")));

 				}

 				if (jsonWidgetObject.has("otsn:type")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("otsn:type")));

 				}

 				if (jsonWidgetObject.has("otsn:concept")) {
 					out.println(otsnFacet(jsonWidgetObject.getJSONObject("otsn:concept")));

 				}
 			}
 %>
			</td>
			<td style="vertical-align: top;">
				<%
					if (!jsonObject.getJSONObject("rdf:Description").getJSONObject("otsn:results")
									.isNull("otsn:document")
									&& jsonObject.getJSONObject("rdf:Description").getJSONObject("otsn:results")
											.getJSONArray("otsn:document").length() > 0) {
				%>
				<div
					class="demo-options mdl-card mdl-card--expand mdl-shadow--2dp mdl-cell mdl-cell--4-col mdl-cell--3-col-tablet mdl-cell--12-col-desktop">
					<div class="mdl-card__supporting-text mdl-card--expand">
						<%
							JSONArray jsonResultsArray = jsonObject.getJSONObject("rdf:Description")
												.getJSONObject("otsn:results").getJSONArray("otsn:document");
										for (int i = 0; i < jsonResultsArray.length(); i++) {
											JSONObject jsonResult = (JSONObject) jsonResultsArray.get(i);
											String otsnResultTitle = jsonResult.getString("dc:title");
											String otsnResultURL = jsonResult.getString("rdf:about");
											String otsnResultDescription = "";
											String otsnResultDate = jsonResult.getString("dc:date");
											String otsnResultType = (String) jsonResult.getJSONArray("dc:type").get(0);
											JSONArray otsnMetadataArray = null;
											if (jsonResult.has("dc:metadata")) {
												otsnMetadataArray = jsonResult.getJSONArray("dc:metadata");
											}
											boolean otsnResultIsDestaque = jsonResult.getBoolean("sebna:destaque");

											if (jsonResult.has("dc:description")) {
												otsnResultDescription = jsonResult.getString("dc:description");
											}
						%>
						<article
							class="col-boo-12 filter-texto box-institucional space-bottom-30">
						<div>
							<h4>
								<a href="<%=otsnResultURL%>"><%=otsnResultTitle%></a>
							</h4>
						</div>
						<div><%=otsnResultDate%></div>
						<div><%=otsnResultType%></div>
						<div>
							Destaque?
							<%=otsnResultIsDestaque ? "Sim" : "Não"%>
							<div><%=otsnResultDescription%></div>

							<%
								if (otsnMetadataArray != null) {
							%>
							<div>
								Assuntos encontrados:
								<%
								for (int meta_i = 0; meta_i < otsnMetadataArray.length(); meta_i++) {
														JSONObject jsonResultMetadata = (JSONObject) otsnMetadataArray.get(meta_i);
														String otsnMetadataTitle = jsonResultMetadata.getString("text");
														String otsnMetadataURL = replaceUrlSearch(jsonResultMetadata.getString("href"));
							%>
								<a href="<%=otsnMetadataURL%>"><%=otsnMetadataTitle%></a><%=(meta_i != otsnMetadataArray.length() - 1) ? "," : ""%>
								<%
									}
													} else {
								%>
								<div>
									Nenhum assunto encontrado
									<%
									}
								%>
									<div>
						</article>
						<%
							}
						%>
					</div>
				</div> <%
 	}
 %>

			</td>
		</tr>
	</table>
	<%
		} else {
				out.println("Nenhum resultado encontrado.");
			} // END if (hasQRequest)
	%>
</div>
<%
	} catch (Throwable ex) {
		out.println("Erro no portlet <br/>");
		out.println("<b style=\"display:none\">");
		ex.printStackTrace();
		out.println(ex.getClass().getName() + ":" + ex.getLocalizedMessage());
		for (StackTraceElement ste : ex.getStackTrace()) {
			out.println(ste.toString());
		}
		out.println("</b>");
	}
%>
<script src="../bower_components/material-design-lite/material.min.js"></script>
<script src="../bower_components/angular/angular.min.js?v=3.1.78"></script>
<script src="../bower_components/jquery/dist/jquery.min.js?v=3.1.78"></script>
<script src="../js/ctrlMySite.js? v=3.1.78"></script>