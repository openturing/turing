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
							var turPath = $location.path().trim();
							if (turPath.endsWith("/")) {
								turPath = turPath.substring(0,
										turPath.length - 1);
							}
							var turSiteNameSplit = turPath.split('/');
							$scope.turSiteName = turSiteNameSplit[turSiteNameSplit.length - 1];

							$scope.init = function() {

								$scope.turQuery = $location.search().q;
								$scope.turPage = $location.search().p;
								$scope.turLocale = $location.search()._setlocale;
								$scope.turSort = $location.search().sort;
								$scope.turFilterQuery = $location.search()['fq[]'];
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
													$scope.total = response.data["rdf:Description"]["otsn:query-context"]["otsn:count"];
													$scope.results = response.data["rdf:Description"]["otsn:results"]["otsn:document"];
													$scope.pages = response.data["rdf:Description"]["otsn:pagination"]["otsn:page"];
													$scope.facets = response.data["rdf:Description"]["otsn:widget"]["otsn:facet-widget"];
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
								$scope.initURL(href);
							}
							$scope.replaceUrlSearch = function(url) {
								urlFormatted = url.replace("/api/sn/"
										+ $scope.turSiteName + "/search",
										"/sn/" + $scope.turSiteName);
								return urlFormatted;
							}
						} ]);