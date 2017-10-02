turingApp.controller('TurStorageInstanceCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turStorageInstanceResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turStorageInstanceResource) {
			$rootScope.$state = $state;
			$scope.ses = turStorageInstanceResource.query();
		} ]);