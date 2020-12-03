turingApp.controller('TurNLPValidationCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turNLPInstanceResource",
		"turAPIServerService",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turNLPInstanceResource, turAPIServerService) {
			$scope.results = null;
			$scope.text = null;
			$scope.nlpmodel = null;
			$scope.outputFormat = null;
			$rootScope.$state = $state;
			$scope.nlps = turNLPInstanceResource.query({}, function() {
				angular.forEach($scope.nlps, function(value, key) {
					if (value.selected == true) {
						$scope.nlpmodel = value.id;
					}
				})
			});
			$scope.changeView = function(view) {
				text = {
					'text' : $scope.text
				};
				//$scope.outputFormat
				var parameter = JSON.stringify(text);
				$http.post(turAPIServerService.get().concat('/nlp/' + $scope.nlpmodel + '/validate/' + "blazon"),
						parameter).then(function(response) {
					$scope.results = response.data;
				}, function(response) {
					//
				});
			};
		} ]);