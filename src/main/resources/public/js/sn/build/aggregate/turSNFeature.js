turingSNApp.factory('turSNSearch', [
		'$http',
		'turAPIServerService',
		function($http, turAPIServerService) {

			return {
				search : function(turSiteName, query, page, _setlocale, sort, fq) {
					var data = {
						'q' : query,
						'p' : page,
						'_setlocale' : _setlocale,
						'sort' : sort,
						'fq[]' : fq
					};
					var config = {
						params : data,
						headers : {
							'Accept' : 'application/json'
						}
					};

					return $http.get(turAPIServerService.get().concat(
							'/sn/' + turSiteName + '/search'), config);
				},
				searchURL : function(url) {
					urlFormatted = url.replace("/api/",
							"/");
					var config = {
						headers : {
							'Accept' : 'application/json'
						}
					};

					return $http.get(turAPIServerService.get().concat(
							urlFormatted), config);
				}
			}
		} ]);
turingSNApp
		.controller(
				'TurSNMainCtrl',
				[
						"$scope",
						"$http",
						"$window",
						"$state",
						"$rootScope",
						"$translate",
						"$location",
						'turSNSearch',
						'amMoment',
						'vigLocale',
						'$location',
						'$anchorScroll',
						'$sce',
						function($scope, $http, $window, $state, $rootScope,
								$translate, $location, turSNSearch, amMoment,
								vigLocale, $location, $anchorScroll, $sce) {

							$scope.vigLanguage = vigLocale.getLocale()
									.substring(0, 2);
							$translate.use($scope.vigLanguage);							
							amMoment.changeLocale('en');
							$scope.pageCount = 0;
							$scope.pageStart = 0;
							$scope.pageEnd = 0;
							
							$scope.defaultTitleField = "title";
							$scope.defaultDescriptionField = "abstract";
							$scope.defaultTextField = "text";
							$scope.defaultImageField = "image";
							$scope.defaultDateField = "published_date";
							$scope.defaultUrlField = "url";
							
							var turPath = $location.path().trim();
							if (turPath.endsWith("/")) {
								turPath = turPath.substring(0,
										turPath.length - 1);
							}
							var turSiteNameSplit = turPath.split('/');
							$scope.turSiteName = turSiteNameSplit[turSiteNameSplit.length - 1];
							$scope.updateParameters = function() {
								$scope.turQueryString = $location.url();
								$scope.turQuery = $location.search().q;
								$scope.turPage = $location.search().p;
								$scope.turLocale = $location.search()._setlocale;
								$scope.turSort = $location.search().sort;
								$scope.turFilterQuery = $location.search()['fq[]'];

								if ($scope.turQuery == null
										|| $scope.turQuery.trim().length == 0) {
									$scope.turQuery = "*";
								}
								if ($scope.turPage == null
										|| $scope.turPage.trim().length == 0) {
									$scope.turPage = "1";
								}
								if ($scope.turSort == null
										|| $scope.turSort.trim().length == 0) {
									$scope.turSort = "relevance";
								}
							}
							 
							$scope.init = function() {
								$scope.updateParameters();
								$scope.initParams($scope.turQuery,
										$scope.turPage, $scope.turLocale,
										$scope.turSort, $scope.turFilterQuery)
							}
							$scope.initParams = function(q, p, _setlocale,
									sort, fq) {

								turSNSearch
										.search($scope.turSiteName, q, p,
												_setlocale, sort, fq)
										.then(
												function successCallback(
														response) {
													$scope.pageCount = response.data["queryContext"]["count"];
													$scope.pageStart = response.data["queryContext"]["pageStart"];
													$scope.pageEnd = response.data["queryContext"]["pageEnd"];
													$scope.results = response.data["results"]["document"];
													$scope.pages = response.data["pagination"];
													$scope.facets = response.data["widget"]["facet"];
													$scope.facetsToRemove = response.data["widget"]["facetToRemove"];
													
													$scope.defaultTitleField =response.data["queryContext"]["defaultFields"]["title"];
													$scope.defaultDescriptionField = response.data["queryContext"]["defaultFields"]["description"];
													$scope.defaultTextField = response.data["queryContext"]["defaultFields"]["text"];
													$scope.defaultImageField = response.data["queryContext"]["defaultFields"]["image"];
													$scope.defaultDateField = response.data["queryContext"]["defaultFields"]["date"];
													$scope.defaultUrlField = response.data["queryContext"]["defaultFields"]["url"];
													
													// $scope.turSort =
													// response.data["queryContext"]["query"]["sort"];
												},
												function errorCallback(response) {
													// error
												})
							}

							$scope.initURL = function(q, p, _setlocale, sort,
									fq) {
								turSNSearch
										.searchURL(q, p, _setlocale, sort, fq)
										.then(
												function successCallback(
														response) {
													$scope.pageCount = response.data["queryContext"]["count"];
													$scope.pageStart = response.data["queryContext"]["pageStart"];
													$scope.pageEnd = response.data["queryContext"]["pageEnd"];
													$scope.results = response.data["results"]["document"];
													$scope.pages = response.data["pagination"];
													$scope.facets = response.data["widget"]["facet"];
													$scope.facetsToRemove = response.data["widget"]["facetToRemove"];
													// $scope.turSort =
													// response.data["queryContext"]["query"]["sort"];
												},
												function errorCallback(response) {
													// error
												})
							}
							$scope.init();
							$rootScope.$state = $state;
							$scope.turRedirect = function(href) {
								// console.log("turRedirect");
								$location.hash('turHeader');
								$location.url($scope.replaceUrlSearch(href));
								$scope.updateParameters();
								$anchorScroll();
								$scope.initURL(href);
							}

							$scope.replaceUrlSearch = function(url) {
								// console.log("replaceUrlSearch");
								urlFormatted = url.replace("/api/sn/"
										+ $scope.turSiteName + "/search",
										"/sn/" + $scope.turSiteName);
								$location.url(urlFormatted);
								return urlFormatted;
							}

							$scope.turChangeSort = function(turSortParam) {
								// console.log("turChangeSort");
								$scope.updateParameters();
								var browserURL = $scope
										.changeQueryStringParameter(
												$scope.turQueryString, "sort",
												turSortParam);
								browserURL = $scope.changeQueryStringParameter(
										browserURL, "p", $scope.turPage);
								browserURL = $scope.changeQueryStringParameter(
										browserURL, "q", $scope.turQuery);
								var apiURL = browserURL.replace("/sn/"
										+ $scope.turSiteName, "/api/sn/"
										+ $scope.turSiteName + "/search")
								$location.url(browserURL);
								$scope.updateParameters();
								// console.log($scope.turQueryString);
								$scope.initURL(apiURL);

							}

							$scope.changeQueryStringParameter = function(uri,
									key, val) {
								// console.log("changeQueryStringParameter");
								return uri
										.replace(
												new RegExp(
														"([?&]"
																+ key
																+ "(?=[=&#]|$)[^#&]*|(?=#|$))"),
												"&"
														+ key
														+ "="
														+ encodeURIComponent(val))
										.replace(/^([^?&]+)&/, "$1?");
							}
						} ]);
