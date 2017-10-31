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
						function($scope, $http, $window, $state, $rootScope,
								$translate, $location, turSNSearch) {
							$scope.init = function() {
								$scope.turQuery = $location.search().q;
								$scope.turPage = $location.search().p;
								$scope.initParams($scope.turQuery,
										$scope.turPage)
							}
							$scope.initParams = function(q, p) {			
								turSNSearch
										.search(q, p)
										.then(
												function successCallback(
														response) {
													$scope.total = response.data["rdf:Description"]["otsn:query-context"]["otsn:count"];
													$scope.results = response.data["rdf:Description"]["otsn:results"]["otsn:document"];
													$scope.pages = response.data["rdf:Description"]["otsn:pagination"]["otsn:page"];
												},
												function errorCallback(response) {
													// error
												})
							}
							$scope.init();
							$scope.test = "Alexandre";
							$rootScope.$state = $state;
							$scope.turRedirect = function(q, p) {
								// $location.url(url.replace(
								// "/api/otsn/search/theme/json", "/sn/"));

								// $window.location.reload();

								$scope.initParams(q, p);
							}
							$scope.replaceUrlSearch = function(url) {
								urlFormatted = url.replace(
										"/api/otsn/search/theme/json", "/sn/");
								return urlFormatted;
							}
						} ]);