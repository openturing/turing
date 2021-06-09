turingApp.controller('TurMLDataGroupCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turMLDataGroupResource",
		function($scope, $http, $window, $state, $rootScope, $translate,
				turMLDataGroupResource) {
			$rootScope.$state = $state;
			$scope.mlDataGroups = turMLDataGroupResource.query();
		} ]);