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
						function($scope, $http, $window, $state, $rootScope,
								$translate, $location, turSNSearch, amMoment) {
							amMoment.changeLocale('en');
							$scope.total = 0;
							$scope.init = function() {
								$scope.turQuery = $location.search().q;
								$scope.turPage = $location.search().p;
								$scope.turLocale = $location.search()._setlocale;
								$scope.turSort = $location.search().sort;
								$scope.turFilterQuery = $location.search()['fq[]'];
								console.log($scope.turFilterQuery);
								$scope.initParams($scope.turQuery,
										$scope.turPage,$scope.turLocale,$scope.turSort,$scope.turFilterQuery)
							}
							$scope.initParams = function(q, p, _setlocale, sort, fq) {	
								turSNSearch
										.search(q, p, _setlocale, sort, fq)
										.then(
												function successCallback(
														response) {
													$scope.total = response.data["rdf:Description"]["otsn:query-context"]["otsn:count"];
													$scope.results = response.data["rdf:Description"]["otsn:results"]["otsn:document"];
													$scope.pages = response.data["rdf:Description"]["otsn:pagination"]["otsn:page"];
													$scope.facets = response.data["rdf:Description"]["otsn:widget"]["otsn:facet-widget"];
												},
												function errorCallback(response) {
													// error
												})
							}
							
							$scope.initURL = function(q, p, _setlocale, sort, fq) {			
								turSNSearch
										.searchURL(q, p, _setlocale, sort, fq)
										.then(
												function successCallback(
														response) {
													$scope.total = response.data["rdf:Description"]["otsn:query-context"]["otsn:count"];
													$scope.results = response.data["rdf:Description"]["otsn:results"]["otsn:document"];
													$scope.pages = response.data["rdf:Description"]["otsn:pagination"]["otsn:page"];
													$scope.facets = response.data["rdf:Description"]["otsn:widget"]["otsn:facet-widget"];
												},
												function errorCallback(response) {
													// error
												})
							}
							$scope.init();
							$scope.test = "Alexandre";
							$rootScope.$state = $state;
							$scope.turRedirect = function(href) {
								// $location.url(url.replace(
								// "/api/otsn/search/theme/json", "/sn/"));

								// $window.location.reload();

								$scope.initURL(href);
							}
							$scope.replaceUrlSearch = function(url) {
								urlFormatted = url.replace(
										"/api/otsn/search/theme/json", "/sn/");
								return urlFormatted;
							}
						} ]);
turingSNApp.factory('turSNSearch', [
		'$http',
		'turAPIServerService',
		function($http, turAPIServerService) {

			return {
				search : function(query, page, _setlocale, sort, fq) {
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
							'/otsn/search/theme/json'), config);
				},
				searchURL : function(url) {
					console.log("a1:" + url);
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
