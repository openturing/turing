<%--
/*######################################################################################
Copyright 2014 OpenText Corporation All rights reserved.
#####################################################################################*/ 
--%>
<%@ page import="com.vignette.ext.templating.util.RequestContext" %>
<%@ taglib prefix="templating" uri="/WEB-INF/vgnExtTemplating.tld" %>
<%@ taglib prefix="component" uri="/WEB-INF/vgnExtComponentURL.tld" %>
<%@ taglib prefix="messaging" uri="/WEB-INF/vgnExtMessaging.tld" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html" %>
<%@ taglib prefix="i18n" uri="http://jakarta.apache.org/taglibs/i18n-1.0" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<templating:initComponent/>
<templating:initRequestContext var="rc"/>
<c:set var="currentLocale" value="${rc.locale}" />
<c:set var="vgnExtComponentId" value="${component.system.id}" />
<c:set var="searchKeyword" value="${component.keyword}" />
<i18n:bundle baseName="com.vignette.ext.templating.TemplatingJSPMsgs" locale="${currentLocale}"/>
<fmt:setLocale value="${currentLocale}"/>
<div class="vgn-ext-text" id="vgn-ext-search-comp-${vgnExtComponentId}">
	<c:if test="${not empty component.title}">
		<templating:textInlineEdit oid="${vgnExtComponentId}" attributexmlname="vgnExtTemplatingComponentTitle">
			<h1 class="contentTitle"><c:out value="${component.title}"/></h1>
		</templating:textInlineEdit>
		<br />
	</c:if>

	<c:if test="${not empty component.header}">
		<templating:textInlineEdit oid="${vgnExtComponentId}" attributexmlname="vgnExtTemplatingComponentHeader">
			<h1 class="contentHeader"><c:out value="${component.header}"/></h1>
		</templating:textInlineEdit>
		<br />
	</c:if>

	<c:set var="totalPageCount" value="0"/>
	<c:choose>
		<c:when test="${empty searchKeyword}" >
			<br><h3><i18n:message key="searchcomponent.no.search.keyword"/></h3><br>
		</c:when>
		<c:otherwise>
			<c:set var="results" value="${component.results}" />
			<c:choose>
				<c:when test="${not empty results}">
					<ul>
						<br><i18n:message key="searchcomponent.search.results"/> "<b><c:out value="${searchKeyword}"/></b>"
						<br><br><i18n:message key="searchcomponent.search.text.1"/> &nbsp; "<b><c:out value="${searchKeyword}"/></b>" &nbsp; <i18n:message key="searchcomponent.search.text.2"/>
						<br><br>
						<c:forEach items="${results}" var="result">
							<c:set var="totalResults" value="${result.totalNumberOfResults}" />
							<c:set var="name" value="${result.name}" />
							<c:set var="summary" value="${result.summary}" />
							<c:set var="linkUrl" value="${result.link}" />
							<li>
								<a href="${linkUrl}">${name}</a><br/>
								<c:if test="${not empty summary}">
									<a href="${linkUrl}">
										<p>
											${summary}
										</p>
									</a>
								</c:if>
							</li>

						</c:forEach>
					</ul>
				</c:when>
				<c:otherwise>
					<br><i18n:message key="searchcomponent.no.search.results"/> <b><c:out value="${searchKeyword}"/></b>
				</c:otherwise>
			</c:choose>
		</c:otherwise>
	</c:choose>
	<br>
	<c:set var="pages" value="${component.allPages}" />
	<c:choose>
		<c:when test="${not empty pages}">
				<br>
				<br>
				<c:forEach items="${pages}" var="page">
					<c:set var="pageLabel" value="${page.name}" />
					<c:set var="pageNumber" value="${page.pageNumber}" />
					<c:set var="pageLinkUrl" value="${page.link}" />
					<a href="${pageLinkUrl}">${pageLabel}</a>
					&nbsp;
				</c:forEach>
		</c:when>
		<c:otherwise>
			<br><i18n:message key="searchcomponent.no.search.results"/> <b><c:out value="${searchKeyword}"/></b>
		</c:otherwise>
	</c:choose>
	<br>
	<br>
	<br>
	<c:set var="facetTypes" value="${component.facets}" />
	<c:choose>
		<c:when test="${not empty facetTypes}">
			<c:forEach items="${facetTypes}" var="facetType">
				<c:set var="facetTypeName" value="${facetType.name}" />
				<c:set var="facets" value="${facetType.facets}" />
				${facetTypeName}
				<ul>
					<c:forEach items="${facets}" var="facet">
						<c:set var="facetName" value="${facet.name}" />
						<c:set var="facetLinkUrl" value="${facet.link}" />
						<li>
							<a href="${facetLinkUrl}">${facetName}</a><br/>
						</li>
					</c:forEach>
				</ul>
			</c:forEach>
		</c:when>
	</c:choose>
	<br>
	<br>
	<br>
	<c:set var="appliedFacets" value="${component.appliedFacets}" />
	<c:choose>
		<c:when test="${not empty appliedFacets}">
		Applied Facets
			<ul>
				<c:forEach items="${appliedFacets}" var="appliedFacet">
					<c:set var="appliedFacetName" value="${appliedFacet.name}" />
					<c:set var="appliedFacetLinkUrl" value="${appliedFacet.link}" />
					<li>
						<a href="${appliedFacetLinkUrl}">${appliedFacetName}</a><br/>
					</li>
				</c:forEach>
			</ul>
		</c:when>
	</c:choose>


	<br />
	<c:if test="${not empty component.footer}">
		<templating:textInlineEdit oid="${vgnExtComponentId}" attributexmlname="vgnExtTemplatingComponentFooter">
			<h1 class="contentFooter"><c:out value="${component.footer}"/></h1>
		</templating:textInlineEdit>
	</c:if>
</div>



