<%--
/*######################################################################################
Copyright 2014 OpenText Corporation All rights reserved.
#####################################################################################*/ 
--%>

<%@ page import="com.vignette.as.client.javabean.ManagedObject" %>
<%@ page import="com.vignette.as.client.javabean.Channel" %>
<%@ page import="com.vignette.as.client.common.ref.ManagedObjectVCMRef" %>
<%@ page import="com.vignette.as.client.javabean.Site" %>
<%@ page import="com.vignette.logging.LoggingManager" %>
<%@ page import="com.vignette.logging.context.ContextLogger" %>
<%@ page import="com.viglet.turing.ext.templating.client.javabean.TuringSearchComponent" %>
<%@ page import="com.viglet.turing.ext.templating.client.javabean.TuringSearchToolbarComponent" %>
<%@ page import="com.vignette.ext.templating.util.*" %>
<%@ page import="com.vignette.as.ui.common.util.UrlUtils" %>
<%@ page import="java.net.URLEncoder" %>

<%
	ContextLogger LOG = LoggingManager.getContextLogger("searchToolbarView.jsp");

	RequestContext rc = PageUtil.getCurrentRequestContext(pageContext);
	ManagedObject mo = null;

	try{
		mo = rc.getRenderedManagedObject();

		StringBuffer channelPath = new StringBuffer();
		String header = null, footer = null, title = null ;
		String siteName = "";

		if (mo instanceof TuringSearchToolbarComponent)
		{
			TuringSearchToolbarComponent turComponent = (TuringSearchToolbarComponent) mo;
			String vgnExtComponentId = rc.getRenderedManagedObject().getContentManagementId().getId();
			
			String channelId = (String)mo.getAttributeValue( TuringSearchComponent.ATTRIBUTE_CHANNEL_PATH );
			header = (String)mo.getAttribute( TuringSearchComponent.ATTRIBUTE_HEADER ).getValue();
			footer = (String)mo.getAttribute( TuringSearchComponent.ATTRIBUTE_FOOTER ).getValue();
			title = (String)mo.getAttribute( TuringSearchComponent.ATTRIBUTE_TITLE ).getValue();
			String searchKeyword = rc.getParameter(turComponent.getKeywordAttrName());
			String linkURI = "";
			searchKeyword = TemplatingUtil.basicHtmlEscape(searchKeyword);

			if( !TemplatingUtil.isNullOrEmpty( channelId) ){
				Channel channel = (Channel)ContentUtil.getManagedObject(new ManagedObjectVCMRef(channelId));

				if(channel != null) {
					Site site = ContentUtil.getSiteByChannel(channel);
					siteName = site.getName();
				}

				channelPath = channelPath.append( "/").append(siteName);

				String[] nodes = ContentUtil.getBreadcrumbNamePath(channel, true);
				for (String node : nodes) {
					channelPath.append("/").append(node);
				}

				linkURI = XSLPageUtil.buildLinkURI(rc, channel.getContentManagementId().toString(), "", "");
			}

%>
		<script type="text/javascript" >

			(function(){
				var script = document.createElement( 'script' );
				script.type = 'text/javascript';
				script.src = "<%=SysUtil.getViewerContext()%>/scripts/turingSearch.js";
				document.getElementsByTagName('head')[0].appendChild(script);
			})();

		</script>
		<div class="sample-search-toolbar">
<%
			if( !TemplatingUtil.isNullOrEmpty(title) ){
%>
				<h1 class="contentTitle"><%= title %></h1>

				<br>
<%
			}

			if( !TemplatingUtil.isNullOrEmpty(header) ){
%>
				<h1 class="contentHeader"><%= header %></h1>

				<br><br>
<%
			}
%>
<BR>
			<input type="text" id='searchText_<%= vgnExtComponentId %>' width="206" value='<%= ( searchKeyword != null ) ? searchKeyword.trim() : "" %>'
											   onkeypress="if(event.keyCode == 13) { startSearch(this.value, '<%= searchKeyword %>', '<%=linkURI.replaceAll("'", "\\\\'")%>', '<%=turComponent.getKeywordAttrName() %>'>); }"/>
			<input type="button" value="Search" onClick="javascript:startSearch(document.getElementById('searchText_<%= vgnExtComponentId %>').value, '<%= searchKeyword %>', '<%=linkURI.replaceAll("'", "\\\\'")%>', '<%=turComponent.getKeywordAttrName() %>')"/>

<%

			if( !TemplatingUtil.isNullOrEmpty(footer) ){
%>
				<br><br>

				<h1 class="contentFooter"><%= footer %></h1>
<%
			}
%>
		</div>
<%
		}
		else{
			out.println("Not a valid Search Toolbar Component");
		}
	}catch(Exception e){		    
		LOG.error(SysUtil.convertExceptionToString(e));
		out.println("Error rendering Search Toobar Component: " + SysUtil.convertExceptionToString(e));
	}
%>

