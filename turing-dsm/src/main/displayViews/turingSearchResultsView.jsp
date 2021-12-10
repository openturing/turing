

<%@ page import="java.io.IOException,
java.util.List,
java.util.Date,
java.util.Map,
java.text.SimpleDateFormat,
com.vignette.as.client.javabean.ManagedObject,
com.vignette.ext.templating.util.RequestContext,
com.vignette.ext.templating.util.PageUtil,
com.viglet.turing.ext.templating.client.javabean.TuringSearchResultsComponent,
com.viglet.turing.client.sn.TurSNDocument,
com.viglet.turing.client.sn.TurSNDocumentList,
com.viglet.turing.client.sn.TurSNQuery,
com.viglet.turing.client.sn.pagination.TurSNPagination,
com.viglet.turing.client.sn.pagination.TurSNPaginationItem,
com.viglet.turing.client.sn.facet.TurSNFacetField,
com.viglet.turing.client.sn.facet.TurSNFacetFieldValue,
com.viglet.turing.client.sn.response.QueryTurSNResponse"%>
<%
RequestContext rc = PageUtil.getCurrentRequestContext(pageContext);
ManagedObject mo = null;
try{
            mo = rc.getRenderedManagedObject(); 
%>

    <i18n:message key="VGN_EXT_NAME_OF_MANAGED_OBJECT" /> &nbsp;&nbsp; <%= mo.getName() %> 

<%
    }catch(Exception ex){
            // indicate that the following output shouldnt be cached.
            rc.setNoCache(true);
%>
   <i18n:message key="VGN_EXT_EXCEPTION_WHILE_RENDERING_VIEW" />
<%
}

if (mo instanceof TuringSearchResultsComponent)
{
	
	TuringSearchResultsComponent turComponent = (TuringSearchResultsComponent) mo;
	List<TurSNDocument> itens = turComponent.getTuringResults(rc);


%>
Itens = <%= itens.size() %> 

<%
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
	out.println("<BR>" + sdf.format(new Date()));
	if (itens.size() > 0)
	{
		for (TurSNDocument document : itens)
		{
			Map doc = document.getContent().getFields();
			out.println("<BR><a href=\"" + doc.get("url") + "\">" + doc.get("title") + "</a>");
		}
		
		
		out.println("<BR><BR>");
		
		String pageAttrName = turComponent.getPageAttrName();
		String keywordAttrName = turComponent.getKeywordAttrName();
		String keyword = request.getParameter(keywordAttrName);
		List<TurSNPaginationItem> pages = turComponent.getAllTuringPages();
		
		if (pages.size() > 1) 
		{
			/*
			for (TurSNPaginationItem turPage : pages)
			{
				if (turPage.getLabel().equals("FIRST"))
					out.println("<a href=\"" + "?" + keywordAttrName + "=" + keyword + "&" + pageAttrName + "=" + turPage.getPageNumber() + "&" + turComponent.getFacetQueryStringParam(rc) + "\">" + " FIRST " + "</a>");
				else if (turPage.getLabel().equals("LAST"))
					out.println("<a href=\"" + "?" + keywordAttrName + "=" + keyword + "&" + pageAttrName + "=" + turPage.getPageNumber() + "&" + turComponent.getFacetQueryStringParam(rc) + "\">" + " LAST " + "</a>");
				else if (turPage.getLabel().equals("PREVIOUS"))
					out.println("<a href=\"" + "?" + keywordAttrName + "=" + keyword + "&" + pageAttrName + "=" + turPage.getPageNumber() + "&" + turComponent.getFacetQueryStringParam(rc) + "\">" + " PREVIOUS " + "</a>");
				else if (turPage.getLabel().equals("NEXT"))
					out.println("<a href=\"" + "?" + keywordAttrName + "=" + keyword + "&" + pageAttrName + "=" + turPage.getPageNumber() + "&" + turComponent.getFacetQueryStringParam(rc) + "\">" + " NEXT " + "</a>");
				else
					out.println("<a href=\"" + "?" + keywordAttrName + "=" + keyword + "&" + pageAttrName + "=" + turPage.getPageNumber() + "&" + turComponent.getFacetQueryStringParam(rc) + "\">" + turPage.getPageNumber() + "</a>");
			}
			
			out.println("<BR>--------------------<BR>");
			*/
			for (TurSNPaginationItem turPage : pages)
			{
				/*
				if (turPage.getType().equals("FIRST"))
					out.println("<a href=\"" + turComponent.getQueryString(turPage.getQueryParams().get(),rc) + "\">" + " FIRST " + "</a>");
				else if (turPage.getType().equals(""))
					out.println("<a href=\"" + turComponent.getQueryString(turPage.getQueryParams().get(),rc) + "\">" + " LAST " + "</a>");
				else if (turPage.getType().equals(TurSNPagination.PREVIOUS))
					out.println("<a href=\"" + turComponent.getQueryString(turPage.getQueryParams().get(),rc) + "\">" + " PREVIOUS " + "</a>");
				else if (turPage.getType().equals(TurSNPagination.NEXT))
					out.println("<a href=\"" + turComponent.getQueryString(turPage.getQueryParams().get(),rc) + "\">" + " NEXT " + "</a>");
				else
					*/
					out.println("<a href=\"" + turComponent.getQueryString(turPage.getQueryParams().get(),rc) + "\">" + turPage.getLabel() + "</a>");
			}
			
		}
		else
		{
			out.println("<BR> Só tem 1 página");
		}
		
		
		List<TurSNFacetField> facetFields = turComponent.getFacetFields();
		
		
		if (facetFields.size() > 0)
		{
			for (TurSNFacetField facet : facetFields)
			{
				out.println("<BR> <BR> " + facet.getLabel());
				//List<TurSNFacetFieldValue> = facetFields.
				List<TurSNFacetFieldValue> values = facet.getValues().getTurSNFacetFieldValues();
				for (TurSNFacetFieldValue value : values)
				{
					//out.println("<BR>" + value.getLabel());
					//value.getQueryParams().get()
					out.println("<br><a href=\"" + turComponent.getQueryString(value.getQueryParams().get(),rc) + "\">" + value.getLabel() +"(" + value.getCount() + ")</a>");
				}
				
			}
		}
		
		TurSNFacetField appliedFacet = turComponent.getAppliedFacetFields();
		if (null != appliedFacet)
		{
			out.println("<BR>----------------------- <BR> " + appliedFacet.getLabel());
			List<TurSNFacetFieldValue> appliedValues = appliedFacet.getValues().getTurSNFacetFieldValues();
			for (TurSNFacetFieldValue appliedValue : appliedValues)
			{
				out.println("<br><a href=\"" + turComponent.getQueryString(appliedValue.getQueryParams().get(),rc) + "\">" + appliedValue.getLabel() + "(Remove)</a>");
			}
		}
		
		
		
		
		/*
		
		if (facetFields.size() > 0)
		{
			for (TurSNFacetField facet : facetFields)
			{
				out.println("<BR> <BR> " + facet.getLabel());
				
			}
		}
		*/
		
	}
}
%>




