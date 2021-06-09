var turWelcome = angular.module('turWelcome',
		[ 'pascalprecht.translate','ngCookies' ]);

turWelcome
		.config(function($httpProvider) {
			$httpProvider.defaults.headers.common["X-Requested-With"] = 'XMLHttpRequest';
		});

turWelcome.service('turAPIServerService', [
	'$http',
	'$location',
	'$cookies',
	function($http, $location, $cookies) {
		var turProtocol = $location.protocol();
		var turHostname = $location.host();
		var turPort = $location.port();
		var turAPIContext = "/api";
		var turEmbServer = turProtocol + "://" + turHostname + ":"
				+ turPort;
		
		var turEmbAPIServer = turEmbServer + turAPIContext;
		console.log(turEmbServer);

		this.server = function() {

			if ($cookies.get('turServer') != null)
				return $cookies.get('turServer');
			else {
				$http({
					method : 'GET',
					url : turEmbAPIServer + "/v2"
				}).then(function successCallback(response) {
					$cookies.put('turServer', turEmbServer);
				}, function errorCallback(response) {
					$cookies.put('turServer', turEmbServer);
				});
				return turEmbServer;
			}
		}
		this.get = function() {

			if ($cookies.get('turAPIServer') != null)
				return $cookies.get('turAPIServer');
			else {
				$http({
					method : 'GET',
					url : turEmbAPIServer + "/v2"
				}).then(function successCallback(response) {
					$cookies.put('turAPIServer', turEmbAPIServer);
				}, function errorCallback(response) {				
					$cookies.put('turAPIServer', turEmbAPIServer);
				});
				return turEmbAPIServer;
			}
		}
	} ]);


turWelcome.controller('TurWelcomeCtrl', [
		"$scope",
		"$http",
		"$window",
		"turAPIServerService",
		function($scope, $http, $window, turAPIServerService) {

			$scope.showLogin = false;

			var errorUI = function() {
				$('.log-status').addClass('wrong-entry');
				$('.alert').fadeIn(500);
				setTimeout("$('.alert').fadeOut(1500);", 3000);
				$('.form-control').keypress(function() {
					$('.log-status').removeClass('wrong-entry');
				});
			}

			var authenticate = function(credentials) {

				var headers = credentials ? {
					authorization : "Basic "
							+ btoa(credentials.username + ":"
									+ credentials.password)
				} : {};

				$http.get(turAPIServerService
						.get()
						.concat("/v2"), {
					headers : headers
				}).then(function(response) {
					if (response.data.product) {
						$scope.showLogin = false;
						$window.location.href = "/console";
					} else {
						$scope.showLogin = true;
						errorUI();
					}
				}, function() {
					$scope.showLogin = true;
					errorUI();
				});
			}

			$scope.credentials = {};
			$scope.login = function() {
				authenticate($scope.credentials);
			};

			// Check Auth
			$http.get(turAPIServerService
					.get()
					.concat("/v2")).then(function(response) {
				if (response.data.product) {
					$scope.showLogin = false;
					$window.location.href = "/console";
				} else {
					$scope.showLogin = true;
				}
			}, function() {
				$scope.showLogin = true;
			});
		} ]);
