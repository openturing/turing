var turingSNApp = angular.module('turingSNApp', [  'ngCookies','ngResource', 'ngAnimate',
		'ngSanitize', 'ui.router', 'ui.bootstrap', 'pascalprecht.translate' ]);
turingSNApp.config([
		'$stateProvider',
		'$urlRouterProvider',
		'$locationProvider',
		'$translateProvider',
		function($stateProvider, $urlRouterProvider, $locationProvider,
				$translateProvider) {
			$translateProvider.useSanitizeValueStrategy('escaped');
			$locationProvider.html5Mode(true);

		/*	$urlRouterProvider.otherwise('/sn');
			$stateProvider.state('home?q&p', {
				url : '/sn',
				data : {
					pageTitle : 'Home | Viglet Turing'
				}
			})*/
		} ]);
turingSNApp.service('turAPIServerService', [
		'$http',
		'$location',
		'$cookies',
		function($http, $location, $cookies) {
			var turProtocol = $location.protocol();
			var turHostname = $location.host();
			var turPort = $location.port();
			var turAPIContext = "/api";
			var turEmbServer = turProtocol + "://" + turHostname + ":"
					+ turPort + turAPIContext;
			console.log(turEmbServer);

			this.get = function() {

				if ($cookies.get('turAPIServer') != null)
					return $cookies.get('turAPIServer');
				else {
					$http({
						method : 'GET',
						url : turEmbServer
					}).then(function successCallback(response) {
						$cookies.put('turAPIServer', turEmbServer);
					}, function errorCallback(response) {
						$cookies.put('turAPIServer', 'http://localhost:2700' + turAPIContext);

					});
					return turEmbServer;
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
						function($scope, $http, $window, $state, $rootScope,
								$translate, $location, turSNSearch) {
							$scope.init = function() {
								$scope.turQuery = $location.search().q;
								$scope.turPage = $location.search().p;
								$scope.initParams($scope.turQuery,
										$scope.turPage)
							}
							$scope.initParams = function(q, p) {
								console.log($location.search());
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
turingSNApp.factory('turSNSearch', [
		'$http',
		'turAPIServerService',
		function($http, turAPIServerService) {

			return {
				search : function(query, page) {
					var data = {
						'q' : query,
						'sort' : 'relevant',
						'_setlocale' : 'pt',
						'p' : page
					};
					var config = {
						params : data,
						headers : {
							'Accept' : 'application/json'
						}
					};

					return $http.get(turAPIServerService.get().concat(
							'/otsn/search/theme/json'), config);
				}
			}
		} ]);
