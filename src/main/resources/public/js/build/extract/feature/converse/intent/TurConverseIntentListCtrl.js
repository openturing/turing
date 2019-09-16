turingApp.controller('TurConverseIntentListCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turConverseIntentResource",
		function($scope, $http, $window, $state, $rootScope, $translate,turConverseIntentResource) {
			$rootScope.$state = $state;
			$scope.intents = turConverseIntentResource.query();
		} ]);