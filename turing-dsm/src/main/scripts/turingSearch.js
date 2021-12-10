
function startSearch(currentKeyword, previousKeyword, linkURI, keywordParam ){

	previousKeyword = !previousKeyword ? keywordParam + "=" : ( keywordParam + "=" + encodeURIComponent(trim(previousKeyword)) ) ;

	if( !linkURI ){
		linkURI = document.URL;
	}

	if( linkURI.indexOf(keywordParam) !== -1) {
		linkURI = linkURI.replace(previousKeyword, keywordParam + "=" + encodeURIComponent(trim(currentKeyword)));
	}  else {
		linkURI = linkURI + ( ( linkURI.indexOf("?") == -1 ) ? "?" : "&" ) ;
		linkURI = linkURI + keywordParam + "=" + encodeURIComponent(trim(currentKeyword));
	}

	document.location = linkURI;
}

var ComponentRefresh = function( baseURL, webContext ) {

	webContext = (webContext != null && webContext != undefined) ? webContext : "/sites"

	this.baseURL = baseURL || ( document.location.protocol + "//" + document.location.host + webContext + "/render/component" );

	this.fire = function( url, params, handler ){
		if( !url ){
			url = this.baseURL;
		}

		url = url + "?";

		for(var x in params){
			if( !params[x] ){
				continue;
			}
			url = url + x + "=" + params[x] + "&" ;
		}

		url = url.substring(0, url.lastIndexOf("&"));

		var xhr = window.ActiveXObject ? new ActiveXObject("Microsoft.XMLHTTP") : new XMLHttpRequest();
		xhr.open('GET', url , true);

		xhr.onreadystatechange = function() {
			var status = false;

			status = (xhr.readyState == 4);

			if ( status === true ) {
				handler(xhr.responseText, params.vgnextcomponentid);
			}
		}

		xhr.send(null);
	}
}

function paginate( params ){
	if( !params || !params.vgnextcomponentid || !params.vgnextoid || !params.vgnextkeyword){
		return;
	}
	
	if (params.maxPages) {
		
		if(parseInt(params.vgnextsearchresultspageno) == parseInt(params.currentPageNo) ){
			return;
		}
		
		if(parseInt(params.vgnextsearchresultspageno) > parseInt(params.maxPages) ){			
			document.getElementById(params.pageNumberElementId).value = params.currentPageNo;
			return;
		}
		
		if(parseInt(params.vgnextsearchresultspageno) <= 0) {
			document.getElementById(params.pageNumberElementId).value = params.currentPageNo;
			return;
		}
		
		if(isNaN(params.vgnextsearchresultspageno)){
			document.getElementById(params.pageNumberElementId).value = params.currentPageNo;
			return;
		}
	}
	var vui = vui || undefined;

	if( !params.appInstanceName && vui ){
		params.appInstanceName = vui.cps.ui.ice.properties.appInstanceName;
	}

	if( !params.vgnextsearchresultspageno){
		params.vgnextsearchresultspageno = '1';
	}

	params.vgnextkeyword = encodeURIComponent(trim(params.vgnextkeyword));
	var	handler = params.handler ? params.handler : function(html, componentOid){
		var component = document.getElementById(params.compDivId || "vgn-ext-search-comp-" + componentOid);
		if (component) {
			component.innerHTML = html;
		}
	};

	var componentRefresh = new ComponentRefresh(null, params.webcontext);
	componentRefresh.fire(null, params, handler);
}

function trim(string) {
	return string.replace(/^\s+|\s+$/g,"");
}

function paginateResults(params, context) {
	
	var handler = function (html, componentId) {
		var elementId = "vgn-ext-query-comp-pagination-div-"+componentId;
		//DIV will be reloaded with the response
		document.getElementById(elementId).innerHTML = html;
		if(typeof ice !== 'undefined' && ice && ice.component){
			vui.publish( ice.component.TOPIC, {type : ice.component.ICE_COMPONENT_CHANGED_EVENT ,
				componentId : componentId} );
		}
	};
	var componentRefresh = new ComponentRefresh(null, context);
	componentRefresh.fire(null, params, handler);
}