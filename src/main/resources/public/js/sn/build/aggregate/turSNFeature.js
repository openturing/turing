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
						function($scope, $http, $window, $state, $rootScope,
								$translate, $location, turSNSearch, amMoment,
								vigLocale, $location, $anchorScroll) {

							$scope.vigLanguage = vigLocale.getLocale()
									.substring(0, 2);
							$translate.use($scope.vigLanguage);

							amMoment.changeLocale('en');
							$scope.pageCount = 0;
							$scope.pageStart = 0;
							$scope.pageEnd = 0;
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
								if (q == null || q.trim().length == 0) {
									q = "*";
								}
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
												},
												function errorCallback(response) {
													// error
												})
							}
							$scope.init();
							$rootScope.$state = $state;
							$scope.turRedirect = function(href) {
								$location.hash('turHeader');
								$anchorScroll();
								$scope.initURL(href);
							}
							$scope.replaceUrlSearch = function(url) {
								urlFormatted = url.replace("/api/sn/"
										+ $scope.turSiteName + "/search",
										"/sn/" + $scope.turSiteName);
								return urlFormatted;
							}
						} ]);
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
