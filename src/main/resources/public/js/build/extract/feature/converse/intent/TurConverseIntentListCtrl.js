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

			$scope.removeIntent = function (intent, index) {
				turConverseIntentResource.delete({id: intent.id});
				$scope.intents.splice(index, 1);

			}
		} ]);