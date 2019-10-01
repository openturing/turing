turingApp.controller('TurConverseIntentListCtrl', [
	"$scope",
	"$http",
	"$window",
	"$state",
	"$stateParams",
	"$rootScope",	
	"$translate",
	"turConverseIntentResource",
	function ($scope, $http, $window, $state, $stateParams, $rootScope, $translate,turConverseIntentResource) {
		$rootScope.$state = $state;
		$scope.agentId = $stateParams.agentId;

		$scope.removeIntent = function (intent, index) {
			turConverseIntentResource.delete({ id: intent.id });
			$scope.intents.splice(index, 1);

		}
	}]);