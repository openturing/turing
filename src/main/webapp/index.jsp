<%@ page import="com.google.api.client.googleapis.auth.oauth2.*"%>
<%@ page import="com.google.api.services.plus.*"%>
<%@ page import="com.google.api.services.plus.Plus.*"%>
<%@ page import="com.google.api.services.plus.model.*"%>
<%@ page import="com.google.gson.Gson"%>
<%@ page import="com.viglet.turing.auth.google.*"%>
<%@ page import="java.io.IOException"%>
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
<%
	String tokenData = (String) request.getSession().getAttribute("token");
	if (tokenData == null) {
%>
<head>
<META http-equiv="refresh" content="0;URL=/turing/welcome" />
</head>
</html>

<%
	} else {

		Person me = null;
		try {

			SigninServlet signinServlet = new SigninServlet();

			// Build credential from stored token data.
			GoogleCredential credential = new GoogleCredential.Builder()
					.setJsonFactory(signinServlet.JSON_FACTORY).setTransport(signinServlet.TRANSPORT)
					.setClientSecrets(signinServlet.CLIENT_ID, signinServlet.CLIENT_SECRET).build()
					.setFromTokenResponse(
							signinServlet.JSON_FACTORY.fromString(tokenData, GoogleTokenResponse.class));
			// Create a new authorized API client.
			Plus service = new Plus.Builder(signinServlet.TRANSPORT, signinServlet.JSON_FACTORY, credential)
					.setApplicationName(signinServlet.APPLICATION_NAME).build();
			// Get a list of people that this user has shared with this app.	 

			me = service.people().get("me").execute();
		} catch (IOException e) {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			//  response.getWriter().print(signinServlet.GSON.toJson("Failed to read data from Google. " +
			//     e.getMessage()));
		}
%>

<head>
<meta charset="utf-8" />
<meta http-equiv="content-type"
	content="application/xhtml+xml; charset=UTF-8" />
<meta http-equiv="X-UA-Compatible" content="IE=edge" />
<meta name="description"
	content="A front-end template that helps you build fast, modern mobile web apps." />
<meta name="viewport"
	content="width=device-width, initial-scale=1.0, minimum-scale=1.0" />
<title>Viglet Turing</title>

<!-- Add to homescreen for Chrome on Android -->
<meta name="mobile-web-app-capable" content="yes" />
<link rel="icon" sizes="192x192" href="images/android-desktop.png" />

<!-- Add to homescreen for Safari on iOS -->
<meta name="apple-mobile-web-app-capable" content="yes" />
<meta name="apple-mobile-web-app-status-bar-style" content="black" />
<meta name="apple-mobile-web-app-title" content="Material Design Lite" />
<link rel="apple-touch-icon-precomposed" href="images/ios-desktop.png" />

<!-- Tile icon for Win8 (144x144 + tile color) -->
<meta name="msapplication-TileImage"
	content="images/touch/ms-touch-icon-144x144-precomposed.png" />
<meta name="msapplication-TileColor" content="#3372DF" />

<link rel="shortcut icon" href="images/favicon.png" />

<!-- SEO: If your mobile URL is different from the desktop URL, add a canonical link to the desktop page https://developers.google.com/webmasters/smartphone-sites/feature-phones -->
<!--
        <link rel="canonical" href="http://www.example.com/">
        -->

<link rel="stylesheet"
	href="https://fonts.googleapis.com/css?family=Roboto:regular,bold,italic,thin,light,bolditalic,black,medium&amp;lang=en" />
<link rel="stylesheet"
	href="https://fonts.googleapis.com/icon?family=Material+Icons" />
<link rel="stylesheet"
	href="https://code.getmdl.io/1.3.0/material.deep_orange-orange.min.css" />
<link rel="stylesheet"
	href="bower_components/mdl-selectfield/dist/mdl-selectfield.min.css" />
<link rel="stylesheet" href="styles.css" />
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

<body ng-app="vigletApp">
	<div
		class="mdl-layout mdl-js-layout mdl-layout--fixed-header">
		<header
			class="demo-header mdl-layout__header ">
		<div class="mdl-layout__header-row">
			<span class="mdl-layout-title">Viglet Turing</span>
			<div class="mdl-layout-spacer"></div>
			<img
				src="<%=me.getImage().getUrl()%>" class="demo-avatar">
		
		</div>

		<div class="mdl-layout__header-row mdl-color--grey-100 mdl-color-text--grey-600">
			<span class="mdl-layout-title">Home</span>
			<div class="mdl-layout-spacer"></div>
				<button
				class="mdl-button mdl-js-button mdl-js-ripple-effect mdl-button--icon"
				id="hdrbtn">
				<i class="material-icons">more_vert</i>
			</button>
			<ul
				class="mdl-menu mdl-js-menu mdl-js-ripple-effect mdl-menu--bottom-right"
				for="hdrbtn">
				<li class="mdl-menu__item">Sobre</li>
				<li class="mdl-menu__item">Contato</li>
				<li class="mdl-menu__item">Privacidade</li>
			</ul>

		</div>
		</header>
		<div
			class="mdl-layout__drawer">
			<nav class="mdl-navigation" style="margin-top:100px">
			<a class="mdl-navigation__link" href="#home"><i
				class="material-icons"
				role="presentation">home</i>Home</a> <a class="mdl-navigation__link"
				href="#nlp"><i
				class="material-icons"
				role="presentation">text_format</i>NLP</a> <a
				class="mdl-navigation__link" href="#ml"><i
				class="material-icons"
				role="presentation">gesture</i>Machine Learning</a> <a
				class="mdl-navigation__link" href="#search-engine"><i
				class="material-icons"
				role="presentation">find_in_page</i>Search Engine</a> <a
				class="mdl-navigation__link" href="#database"><i
				class="material-icons"
				role="presentation">crop_portrait</i>Database</a> <a
				class="mdl-navigation__link" href=""><i
				class="material-icons"
				role="presentation">supervisor_account</i>Users</a> <a
				class="mdl-navigation__link" href=""><i
				class="material-icons"
				role="presentation">local_offer</i>Content</a>
			<div class="mdl-layout-spacer"></div>
			<a class="mdl-navigation__link" href=""><i
				class="material-icons"
				role="presentation">help_outline</i><span class="visuallyhidden">Help</span></a>
			</nav>
		</div>
		<main class="mdl-layout__content">
		<div class="ng-view mdl-grid"></div>
		</main>
	</div>
	<script src="bower_components/material-design-lite/material.min.js"></script>
	<script
		src="bower_components/mdl-selectfield/dist/mdl-selectfield.min.js"></script>
	<script src="bower_components/angular/angular.min.js"></script>
	<script src="bower_components/jquery/dist/jquery.min.js"></script>
	<script
		src="bower_components/moment/min/moment-with-locales.min.js"></script>
	<script
		src="bower_components/moment-timezone/builds/moment-timezone.min.js"></script>
	<script
		src="bower_components/angular-moment/angular-moment.min.js"></script>
	<script
		src="bower_components/angular-animate/angular-animate.min.js"></script>
	<script
		src="bower_components/angular-route/angular-route.min.js"></script>
	<script
		src="bower_components/angular-sanitize/angular-sanitize.min.js"></script>
	<script
		src="bower_components/angular-translate/angular-translate.min.js"></script>
	<script
		src="bower_components/angular-bootstrap-lightbox/dist/angular-bootstrap-lightbox.min.js"></script>
	<script
		src='bower_components/ngInfiniteScroll/build/ng-infinite-scroll.min.js'></script>
	<script src="js/turing.js"></script>

</body>

</html>
<%
	}
%>