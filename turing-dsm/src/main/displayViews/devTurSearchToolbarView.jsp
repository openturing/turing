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



<style>
* { box-sizing: border-box; }
body {
  font: 16px Arial;
}
.autocomplete {
  /*the container must be positioned relative:*/
  position: relative;
  display: inline-block;
}
input {
  border: 1px solid transparent;
  background-color: #f1f1f1;
  padding: 10px;
  font-size: 16px;
}
input[type=text] {
  background-color: #f1f1f1;
  width: 100%;
}
input[type=submit] {
  background-color: DodgerBlue;
  color: #fff;
}
.autocomplete-items {
  position: absolute;
  border: 1px solid #d4d4d4;
  border-bottom: none;
  border-top: none;
  z-index: 99;
  /*position the autocomplete items to be the same width as the container:*/
  top: 100%;
  left: 0;
  right: 0;
}
.autocomplete-items div {
  padding: 10px;
  cursor: pointer;
  background-color: #fff;
  border-bottom: 1px solid #d4d4d4;
}
.autocomplete-items div:hover {
  /*when hovering an item:*/
  background-color: #e9e9e9;
}
.autocomplete-active {
  /*when navigating through the items using the arrow keys:*/
  background-color: DodgerBlue !important;
  color: #ffffff;
}
</style>

<script>
    var countries = ["Afghanistan","Albania","Algeria","Andorra","Angola","Anguilla","Antigua &amp; Barbuda","Argentina","Armenia","Aruba","Australia","Austria","Azerbaijan","Bahamas","Bahrain","Bangladesh","Barbados","Belarus","Belgium","Belize","Benin","Bermuda","Bhutan","Bolivia","Bosnia &amp; Herzegovina","Botswana","Brazil","British Virgin Islands","Brunei","Bulgaria","Burkina Faso","Burundi","Cambodia","Cameroon","Canada","Cape Verde","Cayman Islands","Central Arfrican Republic","Chad","Chile","China","Colombia","Congo","Cook Islands","Costa Rica","Cote D Ivoire","Croatia","Cuba","Curacao","Cyprus","Czech Republic","Denmark","Djibouti","Dominica","Dominican Republic","Ecuador","Egypt","El Salvador","Equatorial Guinea","Eritrea","Estonia","Ethiopia","Falkland Islands","Faroe Islands","Fiji","Finland","France","French Polynesia","French West Indies","Gabon","Gambia","Georgia","Germany","Ghana","Gibraltar","Greece","Greenland","Grenada","Guam","Guatemala","Guernsey","Guinea","Guinea Bissau","Guyana","Haiti","Honduras","Hong Kong","Hungary","Iceland","India","Indonesia","Iran","Iraq","Ireland","Isle of Man","Israel","Italy","Jamaica","Japan","Jersey","Jordan","Kazakhstan","Kenya","Kiribati","Kosovo","Kuwait","Kyrgyzstan","Laos","Latvia","Lebanon","Lesotho","Liberia","Libya","Liechtenstein","Lithuania","Luxembourg","Macau","Macedonia","Madagascar","Malawi","Malaysia","Maldives","Mali","Malta","Marshall Islands","Mauritania","Mauritius","Mexico","Micronesia","Moldova","Monaco","Mongolia","Montenegro","Montserrat","Morocco","Mozambique","Myanmar","Namibia","Nauro","Nepal","Netherlands","Netherlands Antilles","New Caledonia","New Zealand","Nicaragua","Niger","Nigeria","North Korea","Norway","Oman","Pakistan","Palau","Palestine","Panama","Papua New Guinea","Paraguay","Peru","Philippines","Poland","Portugal","Puerto Rico","Qatar","Reunion","Romania","Russia","Rwanda","Saint Pierre &amp; Miquelon","Samoa","San Marino","Sao Tome and Principe","Saudi Arabia","Senegal","Serbia","Seychelles","Sierra Leone","Singapore","Slovakia","Slovenia","Solomon Islands","Somalia","South Africa","South Korea","South Sudan","Spain","Sri Lanka","St Kitts &amp; Nevis","St Lucia","St Vincent","Sudan","Suriname","Swaziland","Sweden","Switzerland","Syria","Taiwan","Tajikistan","Tanzania","Thailand","Timor L'Este","Togo","Tonga","Trinidad &amp; Tobago","Tunisia","Turkey","Turkmenistan","Turks &amp; Caicos","Tuvalu","Uganda","Ukraine","United Arab Emirates","United Kingdom","United States of America","Uruguay","Uzbekistan","Vanuatu","Vatican City","Venezuela","Vietnam","Virgin Islands (US)","Yemen","Zambia","Zimbabwe"];

function autocomplete(inp, arr) {
  /*the autocomplete function takes two arguments,
  the text field element and an array of possible autocompleted values:*/
  var currentFocus;
  /*execute a function when someone writes in the text field:*/
  inp.addEventListener("input", function(e) {
      var a, b, i, val = this.value;
      /*close any already open lists of autocompleted values*/
      closeAllLists();
      if (!val) { return false;}
      currentFocus = -1;
      /*create a DIV element that will contain the items (values):*/
      a = document.createElement("DIV");
      a.setAttribute("id", this.id + "autocomplete-list");
      a.setAttribute("class", "autocomplete-items");
      /*append the DIV element as a child of the autocomplete container:*/
      this.parentNode.appendChild(a);
      /*for each item in the array...*/
      for (i = 0; i < arr.length; i++) {
        /*check if the item starts with the same letters as the text field value:*/
        if (arr[i].substr(0, val.length).toUpperCase() == val.toUpperCase()) {
          /*create a DIV element for each matching element:*/
          b = document.createElement("DIV");
          /*make the matching letters bold:*/
          b.innerHTML = "<strong>" + arr[i].substr(0, val.length) + "</strong>";
          b.innerHTML += arr[i].substr(val.length);
          /*insert a input field that will hold the current array item's value:*/
          b.innerHTML += "<input type='hidden' value='" + arr[i] + "'>";
          /*execute a function when someone clicks on the item value (DIV element):*/
              b.addEventListener("click", function(e) {
              /*insert the value for the autocomplete text field:*/
              inp.value = this.getElementsByTagName("input")[0].value;
              /*close the list of autocompleted values,
              (or any other open lists of autocompleted values:*/
              closeAllLists();
          });
          a.appendChild(b);
        }
      }
  });
  /*execute a function presses a key on the keyboard:*/
  inp.addEventListener("keydown", function(e) {
      var x = document.getElementById(this.id + "autocomplete-list");
      if (x) x = x.getElementsByTagName("div");
      if (e.keyCode == 40) {
        /*If the arrow DOWN key is pressed,
        increase the currentFocus variable:*/
        currentFocus++;
        /*and and make the current item more visible:*/
        addActive(x);
      } else if (e.keyCode == 38) { //up
        /*If the arrow UP key is pressed,
        decrease the currentFocus variable:*/
        currentFocus--;
        /*and and make the current item more visible:*/
        addActive(x);
      } else if (e.keyCode == 13) {
        /*If the ENTER key is pressed, prevent the form from being submitted,*/
        e.preventDefault();
        if (currentFocus > -1) {
          /*and simulate a click on the "active" item:*/
          if (x) x[currentFocus].click();
        }
      }
  });
  function addActive(x) {
    /*a function to classify an item as "active":*/
    if (!x) return false;
    /*start by removing the "active" class on all items:*/
    removeActive(x);
    if (currentFocus >= x.length) currentFocus = 0;
    if (currentFocus < 0) currentFocus = (x.length - 1);
    /*add class "autocomplete-active":*/
    x[currentFocus].classList.add("autocomplete-active");
  }
  function removeActive(x) {
    /*a function to remove the "active" class from all autocomplete items:*/
    for (var i = 0; i < x.length; i++) {
      x[i].classList.remove("autocomplete-active");
    }
  }
  function closeAllLists(elmnt) {
    /*close all autocomplete lists in the document,
    except the one passed as an argument:*/
    var x = document.getElementsByClassName("autocomplete-items");
    for (var i = 0; i < x.length; i++) {
      if (elmnt != x[i] && elmnt != inp) {
      x[i].parentNode.removeChild(x[i]);
    }
  }
}
/*execute a function when someone clicks in the document:*/
document.addEventListener("click", function (e) {
    closeAllLists(e.target);
});
}








</script>

<div class="turing-search-toolbar-test">

<form autocomplete="off" action="/action_page.php">
  <div class="autocomplete" style="width:300px;">
    <input id="myInput" type="text" name="myCountry" placeholder="Country">
  </div>
  <input type="submit">
</form>
</div>

<script>
autocomplete(document.getElementById("myInput"), countries);
</script>
