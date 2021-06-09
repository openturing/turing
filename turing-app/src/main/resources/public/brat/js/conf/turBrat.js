var turingApp = angular.module('turingApp', [ 'ngCookies', 'ngResource',
		'ngAnimate', 'ngSanitize', 'ui.router', 'ui.bootstrap',
		'pascalprecht.translate' ]);

turingApp.controller('TurBratMainCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		function($scope, $http, $window, $state, $rootScope, $translate) {

			$http.get("http://localhost:8000/brat/api/text.json").then(
					function(response) {
						var webFontURLs = [];
						var collData = response.data.collData;
						var docData = response.data.docData;

						Util.embed(
						// id of the div element where brat should embed the
						// visualisations
						'bratdiv',
						// object containing collection data
						collData,
						// object containing document data
						docData,
						// Array containing locations of the visualisation fonts
						webFontURLs);

					});
		} ]);