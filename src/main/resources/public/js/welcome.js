var turWelcome = angular.module('turWelcome', [ 'pascalprecht.translate',
		'ngCookies' ])

turWelcome
		.config([
				'$httpProvider',
				function($httpProvider) {
					$httpProvider.defaults.headers.common["X-Requested-With"] = 'XMLHttpRequest';
				} ]);

turWelcome.service('shAPIServerService', [ '$http', '$location', '$cookies',
		function($http, $location, $cookies) {
			var shProtocol = $location.protocol();
			var shHostname = $location.host();
			var shPort = $location.port();
			var shAPIContext = "/api";
			var shEmbServer = shProtocol + "://" + shHostname + ":" + shPort;

			var shEmbAPIServer = shEmbServer + shAPIContext;
			console.log(shEmbServer);

			this.server = function() {

				if ($cookies.get('shServer') != null)
					return $cookies.get('shServer');
				else {
					$http({
						method : 'GET',
						url : shEmbAPIServer + "/v2"
					}).then(function successCallback(response) {
						$cookies.put('shServer', shEmbServer);
					}, function errorCallback(response) {
						// $cookies.put('shServer', 'http://localhost:2710');
						$cookies.put('shServer', shEmbServer);
					});
					return shEmbServer;
				}
			}
			this.get = function() {

				if ($cookies.get('shAPIServer') != null)
					return $cookies.get('shAPIServer');
				else {
					$http({
						method : 'GET',
						url : shEmbAPIServer + "/v2"
					}).then(function successCallback(response) {
						$cookies.put('shAPIServer', shEmbAPIServer);
					}, function errorCallback(response) {
						// $cookies.put('shAPIServer', 'http://localhost:2710' +
						// shAPIContext);
						$cookies.put('shAPIServer', shEmbAPIServer);
					});
					return shEmbAPIServer;
				}
			}
		} ]);

turWelcome
		.controller(
				'ShWelcomeCtrl',
				[
						"$scope",
						"$http",
						"$httpParamSerializer",
						"$window",
						"shAPIServerService",
						"$cookies",
						function($scope, $http, $httpParamSerializer, $window,
								shAPIServerService,$cookies) {

							$scope.data = {
								grant_type : "password",
								username : "",
								password : "",
								client_id : "acme"
							};
							$scope.encoded = btoa("acme:acmesecret");

							$scope.showLogin = false;

							var errorUI = function() {
								$('.log-status').addClass('wrong-entry');
								$('.alert').fadeIn(500);
								setTimeout("$('.alert').fadeOut(1500);", 3000);
								$('.form-control').keypress(
										function() {
											$('.log-status').removeClass(
													'wrong-entry');
										});
							}

							var authenticate = function(credentials) {

								var req = {
									method : 'POST',
									url : "http://localhost:2700/oauth/token",
									headers : {
										"Authorization" : "Basic "
												+ $scope.encoded,
										"Content-type" : "application/x-www-form-urlencoded; charset=utf-8"
									},
									data : $httpParamSerializer($scope.data)
								}
								$http(req)
										.then(
												function(data) {
													console.log("Autenticado");
													$http.defaults.headers.common.Authorization = 'Bearer '
															+ data.data.access_token;
													$cookies
															.put(
																	"access_token",
																	data.data.access_token);
													$window.location.href = "/console";
												});
							}

							$scope.login = function() {
								authenticate($scope.data);
							};

							// Check Auth
							$http.get(shAPIServerService.get().concat("/v2"))
									.then(function(response) {
										if (response.data.product) {
											$scope.showLogin = false;
											$window.location.href = "/content";
										} else {
											$scope.showLogin = true;
										}
									}, function() {
										$scope.showLogin = true;
									});
						} ]);
