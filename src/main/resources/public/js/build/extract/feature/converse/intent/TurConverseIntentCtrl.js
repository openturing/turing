turingApp.controller('TurConverseIntentCtrl', [
		"$scope",
		"$http",
		"$window",
		"$state",
		"$rootScope",
		"$translate",
		"turConverseIntentResource",
		"$stateParams",
		function($scope, $http, $window, $state, $rootScope, $translate, turConverseIntentResource, $stateParams) {
			$rootScope.$state = $state;
			$scope.intentId = $stateParams.intentId;
			$scope.intent = turConverseIntentResource.get({
				id: $scope.intentId
			});
		} ]);